package com.bryzek.util.log

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Level, Logger as LogbackLogger}
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters.*

/** Captures what a logger actually wrote, so the specs below assert the string a log backend
  * receives rather than a reconstruction of it.
  */
object LogCapture {

  case class Captured(level: Level, message: String, throwableClass: Option[String])

  /** Runs `f` against a logger with its own name and an attached appender, and returns every event
    * it wrote. The name is per-call so concurrent suites cannot see each other's lines.
    */
  def capture(name: String)(f: org.slf4j.Logger => Unit): Seq[Captured] = {
    val logger = LoggerFactory.getLogger(name).asInstanceOf[LogbackLogger]
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    val previousLevel = logger.getLevel
    logger.setLevel(Level.INFO)
    logger.addAppender(appender)
    try {
      f(logger)
      appender.list.asScala.toList.map { e =>
        Captured(
          level = e.getLevel,
          message = e.getFormattedMessage,
          throwableClass = Option(e.getThrowableProxy).map(_.getClassName)
        )
      }
    } finally {
      logger.detachAppender(appender)
      logger.setLevel(previousLevel)
      appender.stop()
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
