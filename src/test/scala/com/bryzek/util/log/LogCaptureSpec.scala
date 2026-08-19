package com.bryzek.util.log

import ch.qos.logback.classic.{Level, Logger as LogbackLogger}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

class LogCaptureSpec extends AnyWordSpec with Matchers {

  "capture" must {

    "return the events the block wrote, at their own levels" in {
      val captured = LogCapture.capture("log-capture-spec-events") { logger =>
        logger.info("first")
        logger.warn("second")
      }
      captured.map(c => (c.level, c.message)) mustBe Seq((Level.INFO, "first"), (Level.WARN, "second"))
    }

    // The helper must not reach the logger through org.slf4j.LoggerFactory. That factory hands every
    // thread a SubstituteLogger — which is not a logback Logger and cannot be cast to one — for as
    // long as another thread is still binding the backend, so a suite that loses that race fails
    // wholesale under `test` and never under `testOnly`. A context built here is bound before it is
    // used, by construction.
    "hand the block a logback logger from a context of its own, never the process-wide one" in {
      var seen: Option[org.slf4j.Logger] = None
      LogCapture.capture("log-capture-spec-context")(logger => seen = Some(logger))

      val logger = seen.getOrElse(fail("capture never invoked the block"))
      logger mustBe a[LogbackLogger]
      logger.asInstanceOf[LogbackLogger].getLoggerContext must not be theSameInstanceAs(
        LoggerFactory.getILoggerFactory
      )
    }

    "keep concurrent captures apart even when they share a name" in {
      val outer = LogCapture.capture("log-capture-spec-shared") { outerLogger =>
        outerLogger.info("outer")
        LogCapture.captureOne("log-capture-spec-shared")(_.info("inner")).message mustBe "inner"
      }
      outer.map(_.message) mustBe Seq("outer")
    }
  }

  "captureOne" must {
    "fail rather than pick one when the block wrote more than a single line" in {
      an[RuntimeException] must be thrownBy LogCapture.captureOne("log-capture-spec-many") { logger =>
        logger.info("one")
        logger.info("two")
      }
    }
  }
}
