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

    // Long, not Int: the units here are the smallest unit of the amount (cents at the default two
    // decimal places), so an Int caps this function at ~$21.5m and wraps silently above it --
    // producing parts that do not add back up to the amount, which is the one property every
    // caller relies on.
    val multiplier = ("1" + "0" * numberDecimalPlaces).toLong
    val converted = parts.map { f => f.toBigDecimal * amount }
    val convertedRounded: List[Long] = converted.map { v =>
      (v.setScale(numberDecimalPlaces, RoundingMode.HALF_UP) * multiplier).longValue
    }.toList

    // if we need to decrease amounts, start at end of list so that the larger values
    // remain in front. If we are adding, start at head of list
    val epsilon = (amount * multiplier).longValue - convertedRounded.sum
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

  /** Allocates an amount across weights in whole cents, in proportion to each weight, so that the parts always add back
    * up to the original amount. For example, splitting 100 across weights [6000, 4000] -- basis points -- produces
    * [60.00, 40.00], and splitting 100 across [1, 1, 1] produces [33.34, 33.33, 33.33].
    *
    * `splitEvenly(amount, n)` is `splitByWeights(amount, Seq.fill(n)(1L))`: this delegates to the same allocator rather
    * than rounding cents a second way, which is the whole reason it lives here beside `splitEvenly` rather than beside
    * a caller.
    *
    * A weight of zero takes nothing. Weights that are ALL zero say nothing about how to divide the amount, so it is
    * split evenly -- that keeps "the parts add back up to the amount" true of every input rather than conditional on the
    * caller's weights, and it is the same answer the caller has in the absence of any weighting at all.
    *
    * A negative amount (a refund) allocates the exact negation of the same amount positive, so reversing a sale
    * reverses each part of it.
    *
    * The amount is taken to whole cents by truncation, so the parts add back up to it exactly for any amount that is
    * itself a whole number of cents.
    */
  def splitByWeights(
    amount: BigDecimal,
    weights: Seq[Long]
  ): Seq[BigDecimal] = {
    assert(weights.nonEmpty, s"weights cannot be empty")
    assert(weights.forall(_ >= 0), s"weights[${weights.mkString(", ")}] cannot be negative")

    val divisor = weights.foldLeft(0L) { case (acc, w) => BigInt(acc).gcd(BigInt(w)).toLong }
    if (divisor == 0) {
      splitEvenly(amount, weights.size)
    } else {
      // Reducing by the greatest common divisor keeps the proportions identical while keeping the
      // denominator as small as it can be -- [6000, 4000] basis points becomes [3, 2] -- which is what
      // lets Fraction, whose numerator and denominator are Ints, carry weights stated as Longs.
      val reduced = weights.map(_ / divisor)
      val total = reduced.foldLeft(BigInt(0)) { case (acc, w) => acc + w }
      assert(
        total <= BigInt(Int.MaxValue),
        s"weights[${weights.mkString(", ")}] must sum to at most ${Int.MaxValue} once reduced by their greatest common divisor; got $total"
      )
      split(amount, reduced.map { w => Fraction(w.toInt, total.toInt) })
    }
  }

  @tailrec
  private def distribute(
    epsilon: Long,
    elements: List[Long],
    index: Int = 0
  ): Seq[Long] = {
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
