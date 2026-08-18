package com.bryzek.util.log

import cats.data.NonEmptyChain
import org.slf4j.Logger

/** The key/value logging contract every app in this fleet emits through.
  *
  * The RENDERED LINE is an alerting interface, not a formatting detail. [[KeyValueLoggerBuilder]]
  * sorts its keys alphabetically and joins them with `", "`, and the NRQL that reads these lines
  * parses a field back out with `aparse('%<field>: *,%')` — a pattern that needs the COMMA after the
  * field, and that returns null rather than an error when the field is absent. So a renamed, dropped
  * or re-sorted key reads as healthy data rather than as a broken query, across every app pointed at
  * by one query. [[KeyValueLoggerBuilderSpec]] pins both properties.
  *
  * Each app supplies its own injectable implementation (which is where per-app keys such as
  * platform's `environment` are attached); the accumulation and rendering are here so there is one
  * place the format can change.
  */
trait CommonLogger {
  def withKeyValue(name: String, value: String): CommonLogger
  def withKeyValues(name: String, values: Seq[String], max: Int = 10): CommonLogger

  def info(msg: String): Unit
  def warn(msg: String): Unit
  def warn(ex: Throwable, msg: String): Unit
  def error(msg: String): Unit
  def error(ex: Throwable, msg: String): Unit

  /** The line this logger would write for `msg` — the rendered contract, without writing it.
    *
    * On the trait rather than only on [[KeyValueLoggerBuilder]] because the rendered string IS the
    * interface downstream queries read, so any implementation has to be able to be asserted against
    * it directly rather than through a log backend.
    */
  def render(msg: String, ex: Option[Throwable]): String

  final def withKeyValue(name: String, value: Boolean): CommonLogger = withKeyValue(name, value.toString)
  final def withKeyValue(name: String, value: Int): CommonLogger = withKeyValue(name, value.toString)
  final def withKeyValue(name: String, value: Long): CommonLogger = withKeyValue(name, value.toString)
  final def withKeyValue(name: String, value: BigDecimal): CommonLogger = withKeyValue(name, value.toString)
  final def withKeyValue(name: String, value: Option[String]): CommonLogger = {
    value match {
      case None => this
      case Some(v) => withKeyValue(name, v)
    }
  }

  final def withKeyValues(name: String, values: NonEmptyChain[String]): CommonLogger = {
    withKeyValues(name, values.toNonEmptyList.toList)
  }
}

/** Accumulates key/values and renders the one line the log backend receives.
  *
  * Immutable: every `with...` returns a new builder, so a logger held as a field can be decorated
  * per call site without the decorations leaking into the next one.
  */
case class KeyValueLoggerBuilder(
  logger: Logger,
  keyValues: Map[String, String] = Map.empty
) extends CommonLogger {

  override def withKeyValue(name: String, value: String): KeyValueLoggerBuilder = {
    this.copy(keyValues = keyValues ++ Map(name -> value))
  }

  override def withKeyValues(name: String, values: Seq[String], max: Int = 10): KeyValueLoggerBuilder = {
    values.toList match {
      case one :: Nil => withKeyValue(name, one)
      case _ =>
        this.copy(
          keyValues = keyValues ++ values.take(max).zipWithIndex.map { case (v, i) => (s"${name}_${i}", v) }
        )
    }
  }

  override def info(msg: String): Unit = logger.info(render(msg, None))
  override def warn(msg: String): Unit = logger.warn(render(msg, None))
  override def warn(ex: Throwable, msg: String): Unit = logger.warn(render(msg, Some(ex)), ex)
  override def error(msg: String): Unit = logger.error(render(msg, None))
  override def error(ex: Throwable, msg: String): Unit = logger.error(render(msg, Some(ex)), ex)

  /** The emitted line: the message, then the throwable's MESSAGE when there is one, then every
    * key/value sorted by key and joined with `", "`.
    *
    * Public because the sort and the join are the contract the NRQL in the memory and slow-request
    * playbooks parses, so they are asserted directly rather than through a log backend.
    *
    * The throwable contributes only its message. Its stack trace is rendered by logback, which the
    * `warn`/`error` overloads hand the exception to directly. Writing a second copy with
    * `printStackTrace(System.err)` puts it somewhere strictly worse: raw stderr is not forwarded to
    * New Relic, so that copy is unreachable from `dev newrelic logs` and outlives nothing but the
    * pod — measured on 2026-08-10 against acumen-web, where a logback line returned 24 records over
    * 7 days while raw-stderr lines from the same containers returned 0 (ISS-1824). It also arrives
    * detached from the key/values assembled here, so the trace and the request it belonged to cannot
    * be lined up even by someone reading the pod directly.
    */
  override def render(msg: String, ex: Option[Throwable]): String = {
    val exMsg = ex match {
      case None => ""
      case Some(e) => s" ${e.getMessage}"
    }
    val sb = new StringBuilder()
    sb.append(s"$msg$exMsg")
    if (keyValues.nonEmpty) {
      sb.append(" ")
      sb.append(
        keyValues.keys.toList.sorted
          .map { k => s"$k: ${keyValues(k)}" }
          .mkString(", ")
      )
    }
    sb.toString()
  }
}
