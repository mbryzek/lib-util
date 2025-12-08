package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BooleansSpec extends AnyWordSpec with Matchers {

  "parse" must {
    "true" in {
      Seq("t", "true", "yes", "y", " TRUE ").filterNot { v =>
        Booleans.parse(v).getOrElse(false)
      } mustBe Nil
    }

    "false" in {
      Seq("f", "false", "no", "n", " FALSE ").filter { v =>
        Booleans.parse(v).getOrElse(true)
      } mustBe Nil
    }

    "undefined" in {
      Seq("foo", "bar").filter { v =>
        Booleans.parse(v).isDefined
      } mustBe Nil
    }
  }
}
