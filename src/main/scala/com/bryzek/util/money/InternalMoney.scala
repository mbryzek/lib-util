package com.bryzek.util.money

import cats.data.ValidatedNec
import cats.implicits.*

import java.math.RoundingMode as JavaRoundingMode
import java.text.NumberFormat
import java.util.Locale
import scala.math.BigDecimal.RoundingMode
import scala.util.Try

/** A currency, and the number of decimal places an amount in it is carried to.
  *
  * `round` is HALF_UP for the same reason [[InternalMoney.label]] is - see the note there.
  */
sealed trait Currency {
  def iso42173: String
  def numberDecimals: Int
  final def round(amount: BigDecimal): BigDecimal = amount.setScale(numberDecimals, RoundingMode.HALF_UP)
}

object Currency {
  case object Usd extends Currency {
    override def iso42173: String = "USD"

    override def numberDecimals: Int = 2
  }

  def fromString(code: String): Option[Currency] = {
    if (code.toUpperCase().trim == Usd.iso42173) {
      Some(Usd)
    } else {
      None
    }
  }

  def validate(code: String): ValidatedNec[String, Currency] = {
    fromString(code) match {
      case None => s"Invalid currency '$code'".invalidNec
      case Some(c) => c.validNec
    }
  }

  def validate(code: Option[String]): ValidatedNec[String, Option[Currency]] = {
    code match {
      case None => None.validNec
      case Some(c) => validate(c).map(Some(_))
    }
  }
}

case class InternalMoney(amount: BigDecimal, currency: Currency) {

  /** The amount rounded to the currency's own number of decimals. */
  def round(): InternalMoney = copy(amount = currency.round(amount))

  /** ROUNDING IS HALF_UP, DELIBERATELY, AND IT IS THE SAME MODE EVERY OTHER ROUNDING HERE USES.
    *
    * `NumberFormat`'s own default is HALF_EVEN ("banker's rounding"), which renders a half-cent
    * amount to the nearest EVEN cent - so $0.125 labels as "$0.12" and $0.135 as "$0.14". HALF_UP
    * always goes away from zero, which is what a person reading a receipt expects and what
    * [[amountInCents]] and [[Currency.round]] already do. One mode across display, storage and the
    * cents an amount is actually charged in means a labelled total and a charged total never
    * disagree by a cent.
    */
  def label: String = {
    currency match {
      case Currency.Usd => format(Locale.US)
    }
  }

  /** `f.format` is handed `amount.bigDecimal` and NOT `amount`. `NumberFormat.format` takes an
    * `Object`, and it reads an exact decimal only out of a `java.math.BigDecimal` - a
    * `scala.math.BigDecimal` is an ordinary `Number` to it, so it goes through `doubleValue()`
    * first. That loses the amounts this rounds on: 2.005 is not representable as a double, arrives
    * as 2.00499999999999989..., and labels "$2.00" however the rounding mode is set.
    */
  private def format(locale: Locale): String = {
    val f = NumberFormat.getCurrencyInstance(locale)
    f.setMaximumFractionDigits(currency.numberDecimals)
    f.setRoundingMode(JavaRoundingMode.HALF_UP)
    f.format(amount.bigDecimal)
  }

  /** The amount as a whole number of the currency's smallest unit - cents, for USD. Feeds payment
    * amounts rather than display, and rounds HALF_UP like everything else here.
    */
  def amountInCents: Int = {
    val multiplier = BigDecimal(10).pow(currency.numberDecimals)
    (amount * multiplier).setScale(0, RoundingMode.HALF_UP).toIntExact
  }

  def +(other: InternalMoney): InternalMoney = {
    assert(currency == other.currency, "Currencies must match to sum")
    copy(amount = amount + other.amount)
  }

  def +(other: InternalMoneySum): InternalMoney = {
    other match {
      case InternalMoneySum.Zero => this
      case InternalMoneySum.Value(v) => this + v
    }
  }

  def -(other: InternalMoney): InternalMoney = {
    assert(currency == other.currency, "Currencies must match to subtract")
    copy(amount = amount - other.amount)
  }

  def -(other: InternalMoneySum): InternalMoney = {
    other match {
      case InternalMoneySum.Zero => this
      case InternalMoneySum.Value(v) => this - v
    }
  }

  def *(other: InternalMoney): InternalMoney = {
    assert(currency == other.currency, "Currencies must match to multiply")
    *(other.amount)
  }

  def *(other: BigDecimal): InternalMoney = copy(amount = amount * other)
}

object InternalMoney {

  def label(value: BigDecimal): String = usd(value).label

  def usd(value: BigDecimal): InternalMoney = InternalMoney(value, Currency.Usd)

  def validate(amount: BigDecimal, currencyCode: String): ValidatedNec[String, InternalMoney] = {
    Currency.validate(currencyCode).map { currency =>
      InternalMoney(amount, currency)
    }
  }

  /** Parses a user-entered USD amount, tolerating the '$' and the thousands separators a person
    * types.
    */
  def parseUsd(value: String): ValidatedNec[String, InternalMoney] = {
    val amount = value.replaceAll("[\\$\\,]", "")
    Try {
      BigDecimal(amount)
    }.toOption.toValidNec(s"Invalid amount: '$value'").map { v =>
      InternalMoney(v, Currency.Usd)
    }
  }

  def sum(values: Seq[InternalMoney]): InternalMoneySum = {
    values.groupBy(_.currency).toList match {
      case Nil => InternalMoneySum.Zero
      case one :: Nil => InternalMoneySum.Value(InternalMoney(one._2.map(_.amount).sum, one._1))
      case multiple =>
        sys.error(s"Unexpected multiple currencies: ${multiple.map(_._1).map(_.iso42173).mkString(", ")}")
    }
  }
}

/** The sum of a collection of [[InternalMoney]] - `Zero` where there was nothing to add, which is
  * distinct from a zero amount in some particular currency.
  */
sealed trait InternalMoneySum {
  def amount: BigDecimal
}

object InternalMoneySum {
  case object Zero extends InternalMoneySum {
    override def amount: BigDecimal = 0
  }

  case class Value(sum: InternalMoney) extends InternalMoneySum {
    override def amount: BigDecimal = sum.amount
  }
}
