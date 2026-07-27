package com.bryzek.util

import scala.annotation.tailrec
import scala.math.BigDecimal.RoundingMode

object Splitter {

  /** Takes an amount and splits it into n fractional parts as evenly as possible For example, splitting 100 into
    * fractions [1/3, 1/3, 1/3] produces [33.34, 33.33, 33.33] This method ensures:
    *   - the parts always add back up to the original amount
    *   - the difference between any two values is always the smallest possible (ie. .01 if specifying two decimal
    *     places)
    */
  def split(
    amount: BigDecimal,
    parts: Seq[Fraction],
    numberDecimalPlaces: Int = 2
  ): Seq[BigDecimal] = {
    assert(parts.nonEmpty, s"parts cannot be empty")
    assert(Fraction.sum(parts).toBigDecimal == 1, s"parts[${parts.map(_.label)}] must add up to 1")
    assert(numberDecimalPlaces >= 0, s"numberDecimalPlaces[$numberDecimalPlaces] must be >= 0")

    val multiplier = ("1" + "0" * numberDecimalPlaces).toInt
    val converted = parts.map { f => f.toBigDecimal * amount }
    val convertedRounded: List[Int] = converted.map { v =>
      (v.setScale(numberDecimalPlaces, RoundingMode.HALF_UP) * multiplier).intValue
    }.toList

    // if we need to decrease amounts, start at end of list so that the larger values
    // remain in front. If we are adding, start at head of list
    val epsilon = (amount * multiplier).intValue - convertedRounded.sum
    distribute(
      epsilon,
      convertedRounded
    ).map { v => BigDecimal(v / (1.0 * multiplier)) }
  }

  /** Takes an amount and splits it as evenly as possible into an equal number of allocations For example, splitting 100
    * 3 ways produces [33.34, 33.33, 33.33] This method ensures:
    *   - the parts always add back up to the original amount
    *   - the difference between any two values is always the smallest possible (ie. .01 if specifying two decimal
    *     places)
    */
  def splitEvenly(
    amount: BigDecimal,
    numberParts: Int,
    numberDecimalPlaces: Int = 2
  ): Seq[BigDecimal] = {
    split(
      amount,
      (1 to numberParts).map { _ => Fraction(1, numberParts) },
      numberDecimalPlaces = numberDecimalPlaces
    )
  }

  @tailrec
  private def distribute(
    epsilon: Int,
    elements: List[Int],
    index: Int = 0
  ): Seq[Int] = {
    if (epsilon == 0) {
      elements

    } else {
      val actualIndex = index % elements.length

      if (epsilon < 0) {
        distribute(
          epsilon + 1,
          elements.updated(actualIndex, elements(actualIndex) - 1),
          actualIndex + 1
        )
      } else {
        distribute(
          epsilon - 1,
          elements.updated(actualIndex, elements(actualIndex) + 1),
          actualIndex + 1
        )
      }
    }
  }
}
