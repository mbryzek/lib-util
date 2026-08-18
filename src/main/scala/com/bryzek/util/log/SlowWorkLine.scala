package com.bryzek.util.log

/** The one line every app in this fleet emits for a unit of work that took longer than its caller
  * considers normal.
  *
  * The MESSAGE FORMAT is the contract, not an implementation detail — `<label> duration_ms=<n>
  * <k>=<v>...` — because the queries that read it are written once and pointed at every app:
  *
  * {{{
  *   SELECT count(*) FROM Log WHERE entity.name IN ('acumen-web','acumen-job')
  *     AND level = 'WARN' AND message LIKE '%path=/_internal_/healthcheck%'
  *     SINCE 7 days ago FACET entity.name, message
  * }}}
  *
  * `k=v` rather than the `k: v` of [[KeyValueLoggerBuilder]] for exactly that reason: this line is
  * matched with `LIKE`, and a token changing here breaks the daily error/stability triage playbook's
  * slow-request step silently and in the direction that reads as healthy.
  *
  * This carries duration ONLY. Heap is a property of the process, not of whichever unit of work
  * happened to be finishing when it was sampled, so it belongs to the JVM memory metrics line —
  * which samples it once a minute and emits the full pool breakdown. Evaluating a process-global
  * condition once per unit of work does not attribute it to that work; it just restates it at the
  * request rate.
  *
  * The threshold is an argument rather than a constant, because "slow" is a property of the
  * population being measured and not of the clock: an HTTP request that takes 2s is anomalous, a
  * background task that takes 2s is ordinary. Callers pass one of the constants below, or their own.
  */
object SlowWorkLine {

  /** An HTTP request is expected to complete in well under a second, so 2s already means something
    * went wrong or something was unusually large.
    *
    * ONE value across every app on purpose: the point of this instrument is that one app's latency
    * distribution can be compared against another's, and two thresholds would make that comparison a
    * guess.
    */
  val HttpRequestMillis: Long = 2000L

  /** Actor messages are dispatch-loop work — read a queue, hand off, schedule. Anything holding the
    * mailbox for seconds is blocking work that belongs somewhere else.
    */
  val ActorMessageMillis: Long = 2000L

  /** Background tasks legitimately run for tens of seconds: they process CSV uploads, crawl, and call
    * out to LLMs. A minute is the point at which one stops looking like normal throughput. Chosen
    * against the measured distribution over the 30 days to 2026-08-10, in which 61% of platform's
    * instrumented task completions fell between 2s and 5s and 9% ran past a minute — it keeps the
    * ~9% tail that is worth reading and drops the 61% that is simply what a task costs.
    */
  val BackgroundTaskMillis: Long = 60000L

  /** The line to log, or `None` when the work was not slow enough to be worth one.
    *
    * Returning the string rather than logging it keeps the decision and the format testable without
    * a logger, and lets each app emit it through whichever logger it already routes everything else
    * through.
    */
  def lineIfSlow(label: String, durationMs: Long, thresholdMs: Long, fields: (String, String)*): Option[String] = {
    if (durationMs < thresholdMs) {
      None
    } else {
      val kvStr = fields.iterator.map { case (k, v) => s"$k=$v" }.mkString(" ")
      Some(
        if (kvStr.isEmpty) s"$label duration_ms=$durationMs"
        else s"$label duration_ms=$durationMs $kvStr"
      )
    }
  }
}
