package com.bryzek.util.metrics

import com.bryzek.util.log.CommonLogger

import java.lang.management.{MemoryPoolMXBean, MemoryType}

/** The once-a-minute `JvmMemoryMetrics` line every JVM app in this fleet emits, and the pure readings
  * behind it.
  *
  * The field NAMES here are a contract, not a formatting detail: the `platform-memory-improvement`
  * playbook ranks hosts by parsing `heapPercent`, `oldGenUsedMb`, `gcCountDelta` and `gcTimeMsDelta`
  * back out of the message string with NRQL `aparse`, and aparse on a field that is absent returns
  * null rather than an error — so a renamed or dropped field reads as a healthy heap rather than as a
  * broken query.
  *
  * Sampling the beans and reading the connection pool off Play's `Database` stay in each app, because
  * both need dependencies (pekko, Play, HikariCP) that do not belong in a general utility library.
  * Everything that decides what the LINE says is here, so there is one definition of the contract
  * rather than one per app kept in step by a comment.
  */
object JvmMemoryMetrics {

  /** The heap percentage at which the process is considered under pressure — the point at which this
    * line escalates from info to warn, and at which each app's own reclaim path acts.
    */
  val HeapWarnPercent: Long = 80L

  case class PoolBreakdown(
    oldGenUsedMb: Long,
    oldGenMaxMb: Long,
    edenUsedMb: Long,
    survivorUsedMb: Long,
    metaspaceUsedMb: Long
  )

  /** Classifies G1 / Parallel / Shenandoah heap pools into old / eden / survivor by name substring.
    * ZGC generational mode reports young-gen as a single "ZGC Young Generation" pool (no Eden /
    * Survivor split), so on ZGC the young-gen breakdown will read zero — edit the matchers if prod
    * ever switches collectors. Anything unrecognized is ignored; we would rather underreport than
    * misattribute a pool to the wrong generation.
    */
  def summarize(pools: Iterable[MemoryPoolMXBean]): PoolBreakdown = {
    def usedMb(p: MemoryPoolMXBean): Long = math.max(p.getUsage.getUsed, 0L) / (1024 * 1024)
    def maxMb(p: MemoryPoolMXBean): Long = {
      val m = p.getUsage.getMax
      if (m < 0) 0L else m / (1024 * 1024)
    }
    def matches(p: MemoryPoolMXBean, substrings: String*): Boolean =
      substrings.exists(s => p.getName.contains(s))

    val heap = pools.filter(_.getType == MemoryType.HEAP)
    val nonHeap = pools.filter(_.getType == MemoryType.NON_HEAP)

    val oldGen = heap.filter(matches(_, "Old", "Tenured"))
    val eden = heap.filter(matches(_, "Eden"))
    val survivor = heap.filter(matches(_, "Survivor"))
    val metaspace = nonHeap.filter(matches(_, "Metaspace"))

    PoolBreakdown(
      oldGenUsedMb = oldGen.map(usedMb).sum,
      oldGenMaxMb = oldGen.map(maxMb).sum,
      edenUsedMb = eden.map(usedMb).sum,
      survivorUsedMb = survivor.map(usedMb).sum,
      metaspaceUsedMb = metaspace.map(usedMb).sum
    )
  }

  /** Fraction of `maximumPoolSize` at which the pool counts as saturated even though nothing is
    * blocked yet. A pool sitting at 80% is one slow query away from queueing, which is the point at
    * which someone wants to be looking rather than the point at which requests are already waiting.
    */
  private val PoolSaturationFraction = 0.8

  /** The four HikariCP gauges that used to reach New Relic as `Database Connection/HikariCP/...` JMX
    * metrics, plus the `maximumPoolSize` they have to be read against.
    *
    * They live on this line rather than as APM metrics because `jmx.enabled` — the only setting that
    * governs them — also drags in seven constant configuration names per app restated every minute
    * forever (ISS-1870). Once this line carries the gauges, `NEW_RELIC_JMX_ENABLED=false` costs
    * nothing; before it, the flag would remove the only pool-saturation signal an app has.
    */
  case class DbPool(
    activeConnections: Int,
    idleConnections: Int,
    totalConnections: Int,
    threadsAwaitingConnection: Int,
    maximumPoolSize: Int
  ) {

    /** Either something is already blocked waiting for a connection, or the pool is close enough to
      * its ceiling that it is about to be. `maximumPoolSize <= 0` means Hikari never reported one, so
      * only the unambiguous half of the test applies.
      */
    def isSaturated: Boolean = {
      val threshold = math.max(1, math.ceil(maximumPoolSize * PoolSaturationFraction).toInt)
      threadsAwaitingConnection > 0 || (maximumPoolSize > 0 && activeConnections >= threshold)
    }
  }

  /** One tick's worth of JVM memory readings. `gcCount` / `gcTimeMs` are JVM-lifetime totals; the
    * deltas are the movement since the previous sample, which is what alerting can act on.
    */
  case class Sample(
    heapUsedMb: Long,
    heapMaxMb: Long,
    nonHeapUsedMb: Long,
    pools: PoolBreakdown,
    gcCount: Long,
    gcTimeMs: Long,
    gcCountDelta: Long,
    gcTimeMsDelta: Long,
    dbPool: Option[DbPool]
  ) {

    /** Zero rather than a division when the JVM reports no heap maximum. */
    def heapPercent: Long = if (heapMaxMb > 0) heapUsedMb * 100 / heapMaxMb else 0L
  }

  /** Emits the sample as the single `JvmMemoryMetrics` line NewRelic reads.
    *
    * The aparse pattern is `'%<field>: *,%'`, so a parsed field needs a COMMA after it.
    * [[com.bryzek.util.log.KeyValueLoggerBuilder]] sorts its keys alphabetically and joins them with
    * ", ", which leaves only the LAST key unterminated — `totalConnections` when the pool sample is
    * present and `survivorUsedMb` when it is not. Neither is in the parsed set, and that is not luck:
    * a key that sorts after `gcTimeMsDelta`, `heapPercent` or `oldGenUsedMb` is what keeps them
    * parseable, so [[JvmMemoryMetricsSpec]] asserts the comma rather than just the presence of each
    * field, in both shapes. The last key is still readable — `aparse` with no trailing comma
    * (`'%totalConnections: *'`) matches to end of line; verified against NerdGraph on account 7724695
    * with this exact line on 2026-08-11, along with all nine of the others.
    *
    * Split out of the app's actor so that assertion can be made on the real emitted line without
    * running a tick (which on platform would also run the job-tier heap governor and force a Full
    * GC).
    */
  def emit(logger: CommonLogger, sample: Sample): Unit = {
    val jvm = logger
      .withKeyValue("heapUsedMb", sample.heapUsedMb)
      .withKeyValue("heapMaxMb", sample.heapMaxMb)
      .withKeyValue("heapPercent", sample.heapPercent)
      .withKeyValue("oldGenUsedMb", sample.pools.oldGenUsedMb)
      .withKeyValue("oldGenMaxMb", sample.pools.oldGenMaxMb)
      .withKeyValue("edenUsedMb", sample.pools.edenUsedMb)
      .withKeyValue("survivorUsedMb", sample.pools.survivorUsedMb)
      .withKeyValue("metaspaceUsedMb", sample.pools.metaspaceUsedMb)
      .withKeyValue("nonHeapUsedMb", sample.nonHeapUsedMb)
      .withKeyValue("gcCount", sample.gcCount)
      .withKeyValue("gcTimeMs", sample.gcTimeMs)
      .withKeyValue("gcCountDelta", sample.gcCountDelta)
      .withKeyValue("gcTimeMsDelta", sample.gcTimeMsDelta)

    val l = sample.dbPool.fold(jvm) { p =>
      jvm
        .withKeyValue("activeConnections", p.activeConnections)
        .withKeyValue("idleConnections", p.idleConnections)
        .withKeyValue("maximumPoolSize", p.maximumPoolSize)
        .withKeyValue("threadsAwaitingConnection", p.threadsAwaitingConnection)
        .withKeyValue("totalConnections", p.totalConnections)
    }

    // Both messages are kept verbatim for the single-condition cases, and the both-at-once case gets
    // its own rather than dropping one: a pool that saturates *because* the heap is thrashing is
    // exactly when hiding half the diagnosis costs the most.
    (sample.heapPercent >= HeapWarnPercent, sample.dbPool.exists(_.isSaturated)) match {
      case (true, true) => l.warn("JvmMemoryMetrics: Heap pressure and pool saturation detected")
      case (true, false) => l.warn("JvmMemoryMetrics: Heap pressure detected")
      case (false, true) => l.warn("JvmMemoryMetrics: Pool saturation detected")
      case (false, false) => l.info("JvmMemoryMetrics: Heap stats")
    }
  }
}
