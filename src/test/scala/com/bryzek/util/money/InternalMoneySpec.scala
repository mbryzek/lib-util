package com.bryzek.util.money

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNec
import cats.implicits.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InternalMoneySpec extends AnyWordSpec with Matchers {

  private def usd(value: BigDecimal): InternalMoney = InternalMoney.usd(value)

  private def expectValid[T](result: ValidatedNec[String, T]): T = {
    result match {
      case Valid(v) => v
      case Invalid(errors) => sys.error(s"Expected valid but got: ${errors.toList.mkString(", ")}")
    }
  }

  private def expectInvalid[T](result: ValidatedNec[String, T]): Seq[String] = {
    result match {
      case Valid(v) => sys.error(s"Expected invalid but got: $v")
      case Invalid(errors) => errors.toList
    }
  }

  "label" must {
    "format" in {
      usd(0).label mustBe "$0.00"
      usd(5).label mustBe "$5.00"
      usd(5.01).label mustBe "$5.01"
      usd(12.34).label mustBe "$12.34"
      usd(1234.56).label mustBe "$1,234.56"
      usd(-1).label mustBe "-$1.00"
    }

    // The case neither product pinned before this type had one owner: platform labelled through
    // NumberFormat's own HALF_EVEN default, so $0.125 rendered "$0.12" there and "$0.13" in acumen,
    // which rounded HALF_UP before labelling. HALF_UP is the decision - see InternalMoney.label.
    "round a half cent HALF_UP, not to the nearest even cent" in {
      usd(0.125).label mustBe "$0.13"
      usd(0.135).label mustBe "$0.14"
      usd(2.005).label mustBe "$2.01"
      usd(-0.125).label mustBe "-$0.13"
    }

    "match the amount actually charged" in {
      Seq[BigDecimal](0.125, 0.135, 2.005, 1.5).foreach { v =>
        usd(v).label mustBe usd(BigDecimal(usd(v).amountInCents) / 100).label
      }
    }
  }

  "round" in {
    usd(0.125).round() mustBe usd(0.13)
    usd(1.234).round() mustBe usd(1.23)
    usd(5).round() mustBe usd(5.00)
  }

  "amountInCents" in {
    usd(.05).amountInCents mustBe 5
    usd(.5).amountInCents mustBe 50
    usd(5).amountInCents mustBe 500
    usd(5.01).amountInCents mustBe 501
    usd(12.34).amountInCents mustBe 1234
    usd(0.125).amountInCents mustBe 13
  }

  "operators" must {
    "add" in {
      usd(1) + usd(2) mustBe usd(3)
      usd(1) + InternalMoneySum.Zero mustBe usd(1)
      usd(1) + InternalMoneySum.Value(usd(2)) mustBe usd(3)
    }

    "subtract" in {
      usd(3) - usd(2) mustBe usd(1)
      usd(3) - InternalMoneySum.Zero mustBe usd(3)
      usd(3) - InternalMoneySum.Value(usd(2)) mustBe usd(1)
    }

    "multiply" in {
      usd(3) * BigDecimal(2) mustBe usd(6)
      usd(3) * usd(2) mustBe usd(6)
    }
  }

  "sum" must {
    "be Zero when there is nothing to add" in {
      InternalMoney.sum(Nil) mustBe InternalMoneySum.Zero
      InternalMoney.sum(Nil).amount mustBe BigDecimal(0)
    }

    "total one currency" in {
      InternalMoney.sum(Seq(usd(1), usd(2.5))) mustBe InternalMoneySum.Value(usd(3.5))
      InternalMoney.sum(Seq(usd(1), usd(2.5))).amount mustBe BigDecimal(3.5)
    }
  }

  "validate" must {
    "accept a known currency code" in {
      expectValid(InternalMoney.validate(1.23, "usd")) mustBe usd(1.23)
      expectValid(InternalMoney.validate(1.23, " USD ")) mustBe usd(1.23)
    }

    "reject an unknown one" in {
      expectInvalid(InternalMoney.validate(1.23, "eur")) mustBe Seq("Invalid currency 'eur'")
    }
  }

  "parseUsd" must {
    "accept what a person types" in {
      expectValid(InternalMoney.parseUsd("0")) mustBe usd(0)
      expectValid(InternalMoney.parseUsd("1234.56")) mustBe usd(1234.56)
      expectValid(InternalMoney.parseUsd("1,234.56")) mustBe usd(1234.56)
      expectValid(InternalMoney.parseUsd("$1234.56")) mustBe usd(1234.56)
    }

    "reject what is not an amount" in {
      expectInvalid(InternalMoney.parseUsd("")) mustBe Seq("Invalid amount: ''")
      expectInvalid(InternalMoney.parseUsd("foo")) mustBe Seq("Invalid amount: 'foo'")
    }
  }
}
