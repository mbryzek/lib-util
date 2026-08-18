package com.bryzek.util.money

import cats.data.Validated.{Invalid, Valid}
import cats.implicits.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CurrencySpec extends AnyWordSpec with Matchers {

  "fromString" in {
    Currency.fromString("USD") mustBe Some(Currency.Usd)
    Currency.fromString(" usd ") mustBe Some(Currency.Usd)
    Currency.fromString("EUR") mustBe None
  }

  "validate" must {
    "accept a known code" in {
      Currency.validate("USD") mustBe Valid(Currency.Usd)
    }

    "reject an unknown code" in {
      Currency.validate("EUR") match {
        case Invalid(errors) => errors.toList mustBe Seq("Invalid currency 'EUR'")
        case Valid(v) => sys.error(s"Expected invalid but got: $v")
      }
    }

    "treat an absent optional code as valid and absent" in {
      Currency.validate(None: Option[String]) mustBe Valid(None)
      Currency.validate(Some("USD")) mustBe Valid(Some(Currency.Usd))
    }
  }

  "round is HALF_UP" in {
    Currency.Usd.round(BigDecimal(0.125)) mustBe BigDecimal("0.13")
    Currency.Usd.round(BigDecimal(0.135)) mustBe BigDecimal("0.14")
    Currency.Usd.round(BigDecimal(1)) mustBe BigDecimal("1.00")
  }
}
