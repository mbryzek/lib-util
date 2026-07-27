package com.bryzek.util

import cats.data.Validated
import cats.implicits.*

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

/** Represents a fraction like 1/3
  */
case class Fraction(n: Int, d: Int) {

  val label: String = if (n == 0 || d == 1) {
    n.toString
  } else if (n == d) {
    "1"
  } else {
    s"$n/$d"
  }

  def toBigDecimal: BigDecimal = BigDecimal(n) / BigDecimal(d)

  def +(f: Fraction): Fraction = {
    Fraction((n * f.d) + (f.n * d), d * f.d).reduce()
  }

  def -(f: Fraction): Fraction = {
    Fraction((n * f.d) - (f.n * d), d * f.d).reduce()
  }

  def *(multiple: Long): Fraction = {
    Fraction(n * multiple.toInt, d).reduce()
  }

  def reduce(): Fraction = {
    val g = gcd(Math.abs(n), Math.abs(d))
    Fraction(n / g, d / g)
  }

  // Determines the greatest common divisor of two numbers
  @tailrec
  private def gcd(a: Int, b: Int): Int = {
    if (b == 0) {
      a
    } else {
      gcd(b, a % b)
    }
  }

}

object Fraction {

  val One: Fraction = Fraction(1, 1)

  def sum(values: Seq[Fraction]): Fraction = {
    values.toList match {
      case Nil => Fraction(0, 1)
      case _ => {
        values.map(_.d).distinct.toList match {
          case commonDenominator :: Nil => Fraction(values.map(_.n).sum, commonDenominator).reduce()
          case _ => values.reduceLeft(_ + _)
        }
      }
    }
  }

  def parse(value: String): Validated[String, Fraction] = {
    value.split("/").toList match {
      case a :: Nil => {
        a.trim match {
          case "1" => One.valid
          case _ => s"Invalid fraction[$value] - missing '/'".invalid
        }
      }

      case n :: d :: Nil => {
        Try {
          Fraction(n.trim.toInt, d.trim.toInt)
        } match {
          case Success(f) => {
            if (f.d == 0) {
              s"Denominator[${f.d}] must be > 0".invalid
            } else {
              f.valid
            }
          }

          case Failure(_) => {
            s"Numerator and denominator must be integers: $value".invalid
          }
        }
      }

      case _ => {
        s"Invalid fraction[$value]".invalid
      }
    }
  }

}
