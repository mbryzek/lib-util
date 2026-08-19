package com.bryzek.util.log

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, LoggerContext}
import ch.qos.logback.core.read.ListAppender

import scala.jdk.CollectionConverters.*

/** Captures what a logger actually wrote, so the specs below assert the string a log backend
  * receives rather than a reconstruction of it.
  */
object LogCapture {

  case class Captured(level: Level, message: String, throwableClass: Option[String])

  /** Runs `f` against a logback logger in a LoggerContext created for this call alone, and returns
    * every event it wrote.
    *
    * The context is private rather than the process-wide one `org.slf4j.LoggerFactory` hands out,
    * and that is what makes this usable from suites running in parallel. `LoggerFactory.getLogger`
    * returns an `org.slf4j.helpers.SubstituteLogger` — not a logback `Logger` — to every thread that
    * asks while another thread is still binding the backend, so a capture that casts what it gets
    * back fails for whichever suite loses that race, in a whole-suite `test` run and never in a
    * single-suite `testOnly` one. Building the context here binds nothing globally, so there is no
    * window to lose: the events, the levels and the formatting are logback's own either way.
    *
    * Each call getting its own context also means two concurrent captures cannot see each other's
    * lines whatever name they pass.
    */
  def capture(name: String)(f: org.slf4j.Logger => Unit): Seq[Captured] = {
    val context = new LoggerContext()
    context.start()
    try {
      val logger = context.getLogger(name)
      logger.setLevel(Level.INFO)
      val appender = new ListAppender[ILoggingEvent]()
      appender.setContext(context)
      appender.start()
      logger.addAppender(appender)

      f(logger)

      appender.list.asScala.toList.map { e =>
        Captured(
          level = e.getLevel,
          message = e.getFormattedMessage,
          throwableClass = Option(e.getThrowableProxy).map(_.getClassName)
        )
      }
    } finally {
      context.stop()
    }
  }

  /** The one line `f` wrote, or a failure describing how many it actually wrote. */
  def captureOne(name: String)(f: org.slf4j.Logger => Unit): Captured = {
    capture(name)(f) match {
      case one :: Nil => one
      case other => sys.error(s"Expected exactly one log line, found ${other.size}: $other")
    }
  }
}
