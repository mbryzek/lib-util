package com.bryzek.util.log

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.{CompletionException, ExecutionException}
import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}

class FailureMessageSpec extends AnyWordSpec with Matchers {

  "of" must {
    "keep an ordinary exception's own message" in {
      FailureMessage.of(new RuntimeException("boom")) mustBe "boom"
    }

    "name an exception with no message by its class" in {
      FailureMessage.of(new IllegalStateException()) mustBe "java.lang.IllegalStateException"
    }

    "look through the box a Future puts around an Error" in {
      implicit val ec: ExecutionContext = ExecutionContext.global
      val failed = Future[Unit](throw new NotImplementedError("Not implemented: POST /x"))
      val e = intercept[ExecutionException](Await.result(failed, 5.seconds))
      e.getMessage mustBe "Boxed Exception"
      FailureMessage.of(e) mustBe "scala.NotImplementedError: Not implemented: POST /x"
    }

    "look through nested boxes of every kind" in {
      val cause = new IllegalArgumentException("bad input")
      val boxed =
        new CompletionException(new InvocationTargetException(new ExecutionException("Boxed Exception", cause)))
      FailureMessage.of(boxed) mustBe "java.lang.IllegalArgumentException: bad input"
    }

    "keep a box with no cause as it is" in {
      FailureMessage.of(new ExecutionException("plain", null)) mustBe "plain"
    }

    "name a bare Error by class and message" in {
      FailureMessage.of(new AssertionError("x was 2")) mustBe "java.lang.AssertionError: x was 2"
    }
  }
}
