package com.bryzek.util.metrics

import ch.qos.logback.classic.Level
import com.bryzek.util.log.{KeyValueLoggerBuilder, LogCapture}
import com.bryzek.util.metrics.JvmMemoryMetrics.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}
import javax.management.ObjectName
import scala.jdk.CollectionConverters.*

class JvmMemoryMetricsSpec extends AnyWordSpec with Matchers {

  private val HeapUsedMb = 987L
  private val HeapMaxMb = 2000L

  private def pool(
    activeConnections: Int = 3,
    threadsAwaitingConnection: Int = 0,
    maximumPoolSize: Int = 10
  ): DbPool = DbPool(
    activeConnections = activeConnections,
    idleConnections = 7,
    totalConnections = 10,
    threadsAwaitingConnection = threadsAwaitingConnection,
    maximumPoolSize = maximumPoolSize
  )

  private def sample(
    heapUsedMb: Long = HeapUsedMb,
    heapMaxMb: Long = HeapMaxMb,
    dbPool: Option[DbPool] = Some(pool())
  ): Sample = Sample(
    heapUsedMb = heapUsedMb,
    heapMaxMb = heapMaxMb,
    nonHeapUsedMb = 150L,
    pools = PoolBreakdown(
      oldGenUsedMb = 300L,
      oldGenMaxMb = 1500L,
      edenUsedMb = 200L,
      survivorUsedMb = 25L,
      metaspaceUsedMb = 120L
    ),
    gc = GcCounters(count = 900L, timeMs = 8000L, pauseCount = 600L, pauseTimeMs = 5000L),
    gcDelta = GcCounters(count = 7L, timeMs = 42L, pauseCount = 5L, pauseTimeMs = 30L),
    dbPool = dbPool
  )

  /** A bean the JVM does not have, so the classification and the arithmetic can be asserted on
    * numbers this suite chooses rather than on whatever the collector running the build did.
    */
  private def gcBean(name: String, count: Long, timeMs: Long): GarbageCollectorMXBean =
    new GarbageCollectorMXBean {
      override def getCollectionCount: Long = count
      override def getCollectionTime: Long = timeMs
      override def getName: String = name
      override def isValid: Boolean = true
      override def getMemoryPoolNames: Array[String] = Array.empty
      override def getObjectName: ObjectName = new ObjectName("java.lang", "name", ObjectName.quote(name))
    }

  /** Emits through the real builder every app logs through, so the assertions below read the string
    * NewRelic actually receives rather than a reconstruction of it.
    */
  private def emitted(s: Sample): LogCapture.Captured =
    LogCapture.captureOne("jvm-memory-metrics-spec") { logger => emit(KeyValueLoggerBuilder(logger), s) }

  "emit" must {

    // The memory-improvement playbook parses these back out of the message with
    // aparse('%<field>: *,%'), which matches nothing — rather than erroring — if the field is absent
    // or unterminated. So the trailing COMMA is part of the contract: it is only there while some
    // other key sorts after this one, and the logger sorts its keys alphabetically.
    "write every field the memory playbook ranks on in the shape NRQL aparse needs" in {
      val line = emitted(sample()).message

      Map(
        "heapPercent" -> "49",
        "oldGenUsedMb" -> "300",
        "gcCountDelta" -> "7",
        "gcTimeMsDelta" -> "42",
        "gcPauseMs" -> "5000",
        "gcPauseMsDelta" -> "30",
        "gcPauseCount" -> "600",
        "gcPauseCountDelta" -> "5",
        "heapUsedMb" -> HeapUsedMb.toString,
        "heapMaxMb" -> HeapMaxMb.toString
      ).foreach { case (field, value) =>
        line must include(s"$field: $value,")
      }
    }

    "keep the same fields parseable when the pool sample is absent and survivorUsedMb sorts last" in {
      val line = emitted(sample(dbPool = None)).message
      Seq("heapPercent: 49,", "oldGenUsedMb: 300,", "gcCountDelta: 7,", "gcTimeMsDelta: 42,", "gcPauseMsDelta: 30,")
        .foreach { kv =>
          line must include(kv)
        }
      line must not include "activeConnections"
      line must endWith("survivorUsedMb: 25")
    }

    "keep the remaining fields, so nothing already querying them breaks" in {
      val line = emitted(sample()).message
      Seq(
        "oldGenMaxMb: 1500",
        "edenUsedMb: 200",
        "survivorUsedMb: 25",
        "metaspaceUsedMb: 120",
        "nonHeapUsedMb: 150",
        "gcCount: 900",
        "gcTimeMs: 8000",
        "gcPauseCount: 600",
        "gcPauseMs: 5000"
      ).foreach { kv =>
        line must include(kv)
      }
    }

    // The four gauges NEW_RELIC_JMX_ENABLED=false removes from APM (Busy / Idle / Total / Threads
    // Awaiting Count), read against the maximumPoolSize they mean nothing without. Comma-terminated
    // for the same aparse reason as everything above — only totalConnections, which nothing parses,
    // is allowed to sort last.
    "carry the HikariCP pool gauges the JMX metrics used to" in {
      val line = emitted(sample()).message
      Seq(
        "activeConnections: 3,",
        "idleConnections: 7,",
        "maximumPoolSize: 10,",
        "threadsAwaitingConnection: 0,"
      ).foreach { kv =>
        line must include(kv)
      }
      line must endWith("totalConnections: 10")
    }

    "log heap stats at info below the warn threshold" in {
      sample().heapPercent mustBe 49L
      val captured = emitted(sample())
      captured.level mustBe Level.INFO
      captured.message must include("JvmMemoryMetrics: Heap stats")
    }

    "warn once heap reaches the warn threshold" in {
      val s = sample(heapUsedMb = HeapMaxMb * HeapWarnPercent / 100)
      s.heapPercent mustBe HeapWarnPercent
      val captured = emitted(s)
      captured.level mustBe Level.WARN
      captured.message must include("JvmMemoryMetrics: Heap pressure detected")
    }

    "warn on pool saturation while the heap is healthy" in {
      val captured = emitted(sample(dbPool = Some(pool(threadsAwaitingConnection = 1))))
      captured.level mustBe Level.WARN
      captured.message must include("JvmMemoryMetrics: Pool saturation detected")
    }

    "name both when the heap and the pool are in trouble at once" in {
      val s = sample(
        heapUsedMb = HeapMaxMb * HeapWarnPercent / 100,
        dbPool = Some(pool(threadsAwaitingConnection = 1))
      )
      emitted(s).message must include("JvmMemoryMetrics: Heap pressure and pool saturation detected")
    }
  }

  "heapPercent" must {
    "be zero when the JVM reports no heap maximum, rather than dividing by it" in {
      sample(heapMaxMb = 0L).heapPercent mustBe 0L
    }
  }

  "DbPool.isSaturated" must {

    "be false for a pool with idle headroom and nothing waiting" in {
      pool(activeConnections = 7).isSaturated mustBe false
    }

    "be true once the pool reaches 80% of its ceiling, before anything blocks" in {
      pool(activeConnections = 8).isSaturated mustBe true
    }

    "be true whenever a thread is waiting, however small the pool reads" in {
      pool(activeConnections = 0, threadsAwaitingConnection = 1).isSaturated mustBe true
    }

    "not call an idle pool saturated when Hikari reports no maximum" in {
      pool(activeConnections = 0, maximumPoolSize = 0).isSaturated mustBe false
    }
  }

  "GcCounters.isConcurrent" must {

    // The measured bean names, from a live JVM on OpenJDK 17 and 25 under each collector. This is
    // the whole classification, so it is asserted by name rather than left to the substring rule:
    // a collector change that renames a bean has to fail HERE, not silently start reporting
    // concurrent cycle time as application pause.
    "recognize every concurrent-time bean HotSpot exposes" in {
      Seq(
        "G1 Concurrent GC",
        "ZGC Cycles",
        "ZGC Minor Cycles",
        "ZGC Major Cycles",
        "Shenandoah Cycles"
      ).foreach(name => withClue(name)(GcCounters.isConcurrent(name) mustBe true))
    }

    "treat every stop-the-world bean as pause" in {
      Seq(
        "G1 Young Generation",
        "G1 Old Generation",
        "ZGC Pauses",
        "ZGC Minor Pauses",
        "ZGC Major Pauses",
        "Shenandoah Pauses",
        "PS Scavenge",
        "PS MarkSweep",
        "Copy",
        "MarkSweepCompact"
      ).foreach(name => withClue(name)(GcCounters.isConcurrent(name) mustBe false))
    }

    // Wrong in the direction that still alerts, rather than towards a zero nobody can tell from a
    // healthy collector.
    "count a bean it does not recognize as pause" in {
      GcCounters.isConcurrent("Some Future Collector") mustBe false
    }
  }

  "GcCounters.read" must {

    // The numbers are the measured ones: 45s of steady allocation in a 976 MB heap under
    // generational ZGC. Summed whole they say 64,831 ms of GC; the application was stopped for 124.
    "separate stop-the-world pause from concurrent cycle time" in {
      val counters = GcCounters.read(
        Seq(
          gcBean("ZGC Minor Cycles", count = 1200L, timeMs = 22151L),
          gcBean("ZGC Minor Pauses", count = 3600L, timeMs = 75L),
          gcBean("ZGC Major Cycles", count = 40L, timeMs = 42556L),
          gcBean("ZGC Major Pauses", count = 120L, timeMs = 49L)
        )
      )

      counters.timeMs mustBe 64831L
      counters.pauseTimeMs mustBe 124L
      counters.count mustBe 4960L
      counters.pauseCount mustBe 3720L
    }

    "leave G1's two stop-the-world beans as pause and its concurrent one out of it" in {
      val counters = GcCounters.read(
        Seq(
          gcBean("G1 Young Generation", count = 800L, timeMs = 21245L),
          gcBean("G1 Old Generation", count = 12L, timeMs = 13698L),
          gcBean("G1 Concurrent GC", count = 30L, timeMs = 422L)
        )
      )

      counters.pauseTimeMs mustBe 34943L
      counters.timeMs mustBe 35365L
    }

    // -1 is the JVM's "not available"; summing it would walk the total backwards.
    "ignore a bean reporting no counters rather than subtracting it" in {
      val counters = GcCounters.read(
        Seq(gcBean("G1 Young Generation", count = 5L, timeMs = 20L), gcBean("Copy", count = -1L, timeMs = -1L))
      )

      counters mustBe GcCounters(count = 5L, timeMs = 20L, pauseCount = 5L, pauseTimeMs = 20L)
    }

    "read zeros from a JVM exposing no collector at all" in {
      GcCounters.read(Nil) mustBe GcCounters.Zero
    }

    "report a real pause off the live JVM's own beans" in {
      val counters = GcCounters.read(ManagementFactory.getGarbageCollectorMXBeans.asScala.toList)

      counters.pauseTimeMs must be <= counters.timeMs
      counters.pauseCount must be <= counters.count

      // Every collector this build could be running on stops the world for something, so a zero
      // here means the name matchers no longer recognize it — the silent-zero failure that made
      // the summed figure unreadable, arriving in the replacement.
      counters.pauseCount must be > 0L
    }
  }

  "GcCounters.since" must {

    "report the movement between two readings" in {
      val previous = GcCounters(count = 100L, timeMs = 900L, pauseCount = 80L, pauseTimeMs = 400L)
      val current = GcCounters(count = 107L, timeMs = 942L, pauseCount = 85L, pauseTimeMs = 430L)

      current.since(previous) mustBe GcCounters(count = 7L, timeMs = 42L, pauseCount = 5L, pauseTimeMs = 30L)
    }

    // A sampler that restarts re-seeds from the live beans, and a JVM that restarts resets them.
    // Either way the baseline moved, and a negative delta would read as an impossible reading
    // rather than as a reseed.
    "clamp at zero when the baseline is ahead of the reading" in {
      GcCounters.Zero.since(GcCounters(count = 100L, timeMs = 900L, pauseCount = 80L, pauseTimeMs = 400L)) mustBe
        GcCounters.Zero
    }
  }

  "summarize" must {

    "break the live JVM pools down into non-negative generations" in {
      val b = summarize(ManagementFactory.getMemoryPoolMXBeans.asScala.toList)

      b.oldGenUsedMb must be >= 0L
      b.oldGenMaxMb must be >= 0L
      b.edenUsedMb must be >= 0L
      b.survivorUsedMb must be >= 0L
      b.metaspaceUsedMb must be >= 0L

      // Every standard collector exposes Eden plus Old or Tenured, so at least one heap category
      // must be populated — a breakdown of all zeros means the matchers no longer recognize the
      // collector prod is running, which is the silent-zero failure this whole line exists to avoid.
      (b.oldGenUsedMb + b.edenUsedMb + b.survivorUsedMb) must be > 0L

      // Metaspace is always present on HotSpot.
      b.metaspaceUsedMb must be > 0L
    }

    "return zeros for an empty pool list" in {
      summarize(Nil) mustBe PoolBreakdown(0L, 0L, 0L, 0L, 0L)
    }
  }
}
