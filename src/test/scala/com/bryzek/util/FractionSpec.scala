package com.bryzek.util

import cats.data.Validated.{Invalid, Valid}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FractionSpec extends AnyWordSpec with Matchers {

  private def toF(value: String): Fraction =
    Fraction.parse(value).getOrElse(sys.error(s"Invalid fraction[$value]"))

  private def expectError(fraction: String): String = {
    Fraction.parse(fraction) match {
      case Invalid(error) => error
      case Valid(_) => sys.error(s"Expected error for fraction[$fraction]")
    }
  }

  "parse" must {
    "validate" in {
      expectError("x") mustBe "Invalid fraction[x] - missing '/'"
      expectError("1/2/3") mustBe "Invalid fraction[1/2/3]"
      expectError("1/0") mustBe "Denominator[0] must be > 0"
      expectError("1.1/2") mustBe "Numerator and denominator must be integers: 1.1/2"
    }

    "read a whole number" in {
      toF("1") must equal(Fraction.One)
      toF(" 1 ") must equal(Fraction.One)
    }

    "read a fraction, trimming whitespace" in {
      toF("1/2").label must equal("1/2")
      toF("  2 /  9 ").label must equal("2/9")
    }
  }

  "addition" in {
    (toF("1/4") + toF("1/4")).label must equal("1/2")
    (toF("1/2") + toF("1/2")).label must equal("1")
    (toF("1/2") + toF("3/2")).label must equal("2")
  }

  "subtraction" in {
    (toF("1/4") - toF("1/4")).label must equal("0")
    (toF("1/2") - toF("1/2")).label must equal("0")
    (toF("1/2") - toF("1/4")).label must equal("1/4")
    (toF("1/2") - toF("3/2")).label must equal("-1")
  }

  "multiplication" in {
    (toF("1/4") * 2).label must equal("1/2")
    (toF("2/4") * 3).label must equal("3/2")
    (toF("2/4") * 0).label must equal("0")
    (toF("2/4") * -1).label must equal("-1/2")
  }

  "label" in {
    toF("1/4").label must equal("1/4")
    toF("2/4").label must equal("2/4")
    toF("4/4").label must equal("1")
  }

  "toBigDecimal" in {
    toF("1/4").toBigDecimal must equal(BigDecimal(0.25))
  }

  "Fraction.sum" must {
    "common denominator" in {
      val f = Fraction.sum(
        Seq(
          toF("1/3"),
          toF("2/3")
        )
      )
      f.label must equal("1")
      f.n mustBe 1
      f.d mustBe 1
    }

    "different denominator" in {
      val f = Fraction.sum(
        Seq(
          toF("1/2"),
          toF("2/5")
        )
      )
      f.label must equal("9/10")
      f.n mustBe 9
      f.d mustBe 10
    }

    "empty" in {
      Fraction.sum(Nil) must equal(Fraction(0, 1))
    }
  }
}
