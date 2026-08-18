package com.bryzek.util.log

import cats.data.NonEmptyChain
import ch.qos.logback.classic.Level
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.LoggerFactory

class KeyValueLoggerBuilderSpec extends AnyWordSpec with Matchers {

  private def builder: KeyValueLoggerBuilder = KeyValueLoggerBuilder(LoggerFactory.getLogger("key-value-logger-spec"))

  "render" must {

    "return the message alone when there are no key values" in {
      builder.render("hello", None) mustBe "hello"
    }

    // The NRQL that reads these lines parses a field with aparse('%<field>: *,%'), which needs the
    // comma AFTER the field and returns null rather than an error when it is missing. The sort is
    // what decides which key is last and therefore which one is unterminated, so it is asserted
    // exactly rather than by membership.
    "sort keys alphabetically and join them with a comma and a space" in {
      builder
        .withKeyValue("zebra", "z")
        .withKeyValue("apple", "a")
        .withKeyValue("mango", "m")
        .render("msg", None) mustBe "msg apple: a, mango: m, zebra: z"
    }

    "leave every key but the last comma-terminated, which is what aparse needs" in {
      val line = builder.withKeyValue("b", "2").withKeyValue("a", "1").withKeyValue("c", "3").render("msg", None)
      line must include("a: 1,")
      line must include("b: 2,")
      line must endWith("c: 3")
    }

    "append the throwable's message, before the key values" in {
      builder
        .withKeyValue("a", "1")
        .render("failed", Some(new RuntimeException("boom"))) mustBe "failed boom a: 1"
    }

    "render every scalar type through the same key value" in {
      builder
        .withKeyValue("bool", true)
        .withKeyValue("int", 1)
        .withKeyValue("long", 2L)
        .withKeyValue("dec", BigDecimal("3.5"))
        .render("msg", None) mustBe "msg bool: true, dec: 3.5, int: 1, long: 2"
    }

    "drop a None rather than rendering the word" in {
      builder.withKeyValue("a", None: Option[String]).render("msg", None) mustBe "msg"
      builder.withKeyValue("a", Some("1")).render("msg", None) mustBe "msg a: 1"
    }

    "keep the plain key when a sequence holds exactly one value" in {
      builder.withKeyValues("id", Seq("abc")).render("msg", None) mustBe "msg id: abc"
    }

    "index a sequence of several values" in {
      builder.withKeyValues("id", Seq("a", "b")).render("msg", None) mustBe "msg id_0: a, id_1: b"
    }

    "cap a sequence at max, so one runaway list cannot become the whole line" in {
      val line = builder.withKeyValues("id", (1 to 20).map(_.toString), max = 3).render("msg", None)
      line mustBe "msg id_0: 1, id_1: 2, id_2: 3"
    }

    "accept a NonEmptyChain as a sequence" in {
      builder.withKeyValues("id", NonEmptyChain("a", "b")).render("msg", None) mustBe "msg id_0: a, id_1: b"
    }

    "leave the builder it was derived from untouched" in {
      val base = builder.withKeyValue("a", "1")
      base.withKeyValue("b", "2").render("msg", None) mustBe "msg a: 1, b: 2"
      base.render("msg", None) mustBe "msg a: 1"
    }
  }

  "the log methods" must {

    "write the rendered line at their own level" in {
      Seq[(KeyValueLoggerBuilder => Unit, Level)](
        (_.info("msg"), Level.INFO),
        (_.warn("msg"), Level.WARN),
        (_.error("msg"), Level.ERROR)
      ).foreach { case (emit, level) =>
        val captured = LogCapture.captureOne("key-value-logger-spec-levels") { logger =>
          emit(KeyValueLoggerBuilder(logger).withKeyValue("a", "1"))
        }
        captured.level mustBe level
        captured.message mustBe "msg a: 1"
        captured.throwableClass mustBe None
      }
    }

    // logback renders the stack trace from the throwable it is handed; the line itself carries only
    // the message, so it stays greppable and stays attached to the key values (ISS-1824).
    "carry the throwable's message on the line and hand the throwable to the backend" in {
      Seq[(KeyValueLoggerBuilder => Unit, Level)](
        (_.warn(new RuntimeException("boom"), "msg"), Level.WARN),
        (_.error(new RuntimeException("boom"), "msg"), Level.ERROR)
      ).foreach { case (emit, level) =>
        val captured = LogCapture.captureOne("key-value-logger-spec-throwables") { logger =>
          emit(KeyValueLoggerBuilder(logger).withKeyValue("a", "1"))
        }
        captured.level mustBe level
        captured.message mustBe "msg boom a: 1"
        captured.throwableClass mustBe Some("java.lang.RuntimeException")
      }
    }
  }
}
