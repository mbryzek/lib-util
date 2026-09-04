package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SplitterSpec extends AnyWordSpec with Matchers {

  private def toF(value: String): Fraction =
    Fraction.parse(value).getOrElse(sys.error(s"Invalid fraction[$value]"))

  private val amounts: Seq[BigDecimal] = Seq[BigDecimal](0, 0.01, 0.07, 0.11, 3.33, 100, 100.01, 12345.67)

  private val weightings: Seq[Seq[Long]] = Seq(
    Seq(1L),
    Seq(1L, 1L),
    Seq(3L, 1L),
    Seq(6000L, 3000L, 1000L),
    Seq(1L, 0L, 2L),
    Seq(0L, 0L, 0L),
    Seq(7L, 11L, 13L, 17L),
  )

  "split" must {
    "allocate by fraction" in {
      Splitter.split(100, Seq(toF("1"))) must equal(Seq(100.00))
      Splitter.split(100, Seq(toF("1/2"), toF("1/2"))) must equal(Seq(50.00, 50.00))
      Splitter.split(100, Seq(toF("1/3"), toF("1/3"), toF("1/3"))) must equal(Seq(33.34, 33.33, 33.33))
      Splitter.split(100, Seq(toF("1/3"), toF("1/3"), toF("1/3")), numberDecimalPlaces = 0) must equal(Seq(34, 33, 33))
    }

    "allocate uneven fractions" in {
      Splitter.split(100, Seq(toF("1/4"), toF("3/4"))) must equal(Seq(25.00, 75.00))
    }

    "reject parts that do not add up to one" in {
      an[AssertionError] must be thrownBy Splitter.split(100, Seq(toF("1/3"), toF("1/3")))
    }

    "reject empty parts" in {
      an[AssertionError] must be thrownBy Splitter.split(100, Nil)
    }
  }

  "splitEvenly" must {
    "divide into equal parts" in {
      Splitter.splitEvenly(100, 1) must equal(Seq(100.00))
      Splitter.splitEvenly(100, 2) must equal(Seq(50.00, 50.00))
      Splitter.splitEvenly(100, 3) must equal(Seq(33.34, 33.33, 33.33))
      Splitter.splitEvenly(100, 3, numberDecimalPlaces = 0) must equal(Seq(34, 33, 33))
    }

    "allocate the largest split to the last element when rounding up leaves a surplus" in {
      Splitter.splitEvenly(.11, 2) must equal(Seq(.05, .06))
    }

    "minimize the remainder" in {
      Splitter.splitEvenly(3.33, 7) must equal(
        Seq(0.47, 0.47, 0.47, 0.48, 0.48, 0.48, 0.48)
      )
    }

    "split zero" in {
      Splitter.splitEvenly(0, 3) must equal(Seq(0.00, 0.00, 0.00))
    }

    // The property every caller depends on: the parts are an exact allocation of the amount, so
    // summing them per-recipient can never drift from the total that was split.
    "always add back up to the original amount" in {
      Seq[BigDecimal](0, 0.01, 0.11, 3.33, 100, 100.01, 12345.67).foreach { amount =>
        (1 to 9).foreach { parts =>
          val split = Splitter.splitEvenly(amount, parts)
          split.size mustBe parts
          withClue(s"amount[$amount] parts[$parts] split[$split]: ") {
            split.sum mustBe amount
          }
        }
      }
    }

    "keep every part within one cent of every other" in {
      Seq[BigDecimal](0.01, 0.11, 3.33, 100, 100.01).foreach { amount =>
        (1 to 9).foreach { parts =>
          val split = Splitter.splitEvenly(amount, parts)
          withClue(s"amount[$amount] parts[$parts] split[$split]: ") {
            (split.max - split.min) must be <= BigDecimal(0.01)
          }
        }
      }
    }
  }

  "splitByWeights" must {
    "allocate in proportion to the weights" in {
      Splitter.splitByWeights(100, Seq(1L, 1L)) must equal(Seq(50.00, 50.00))
      Splitter.splitByWeights(100, Seq(3L, 1L)) must equal(Seq(75.00, 25.00))
      Splitter.splitByWeights(100, Seq(6000L, 4000L)) must equal(Seq(60.00, 40.00))
      Splitter.splitByWeights(1234.56, Seq(6000L, 4000L)) must equal(Seq(740.74, 493.82))
    }

    "allocate an amount that does not divide" in {
      Splitter.splitByWeights(100.00, Seq(1L, 1L, 1L)) must equal(Seq(33.34, 33.33, 33.33))
    }

    "allocate the whole amount to a single weight" in {
      Splitter.splitByWeights(100, Seq(1L)) must equal(Seq(100.00))
      Splitter.splitByWeights(33.33, Seq(7L)) must equal(Seq(33.33))
      Splitter.splitByWeights(-12.34, Seq(5L)) must equal(Seq(-12.34))
    }

    "give a zero weight nothing" in {
      Splitter.splitByWeights(100, Seq(1L, 0L, 1L)) must equal(Seq(50.00, 0.00, 50.00))
    }

    // Weights that are all zero carry no proportion, and the caller reaching this has an amount to
    // allocate either way -- a fixed adjustment funded by members whose own even share rounded to
    // nothing is exactly the shape that produces it.
    "split evenly when every weight is zero" in {
      Splitter.splitByWeights(100, Seq(0L, 0L, 0L)) must equal(Splitter.splitEvenly(100, 3))
      Splitter.splitByWeights(100, Seq(0L, 0L, 0L)) must equal(Seq(33.34, 33.33, 33.33))
      Splitter.splitByWeights(0, Seq(0L, 0L)) must equal(Seq(0.00, 0.00))
    }

    "reduce weights by their greatest common divisor rather than overflowing the denominator" in {
      Splitter.splitByWeights(100, Seq(2000000000L, 2000000000L)) must equal(Seq(50.00, 50.00))
    }

    // Refunds are signed in the ledger, so a refund of a sale must reverse each part of that sale
    // rather than allocate the odd cent to a different recipient.
    "allocate a negative amount as the exact negation of the positive one" in {
      Splitter.splitByWeights(-100, Seq(1L, 1L, 1L)) must equal(Seq(-33.34, -33.33, -33.33))
      Splitter.splitByWeights(-0.11, Seq(1L, 1L)) must equal(Seq(-0.05, -0.06))

      amounts.foreach { amount =>
        weightings.foreach { weights =>
          withClue(s"amount[$amount] weights[$weights]: ") {
            Splitter.splitByWeights(-amount, weights) must equal(Splitter.splitByWeights(amount, weights).map { v =>
              -v
            })
          }
        }
      }
    }

    // The property the whole allocator exists for, and the reason splitByWeights lives here beside
    // splitEvenly instead of next to its caller: one implementation, one guarantee.
    "always add back up to the original amount" in {
      (amounts ++ amounts.map { v => -v }).foreach { amount =>
        weightings.foreach { weights =>
          val split = Splitter.splitByWeights(amount, weights)
          split.size mustBe weights.size
          withClue(s"amount[$amount] weights[$weights] split[$split]: ") {
            split.sum mustBe amount
          }
        }
      }
    }

    "agree with splitEvenly on equal weights" in {
      (amounts ++ amounts.map { v => -v }).foreach { amount =>
        (1 to 9).foreach { parts =>
          withClue(s"amount[$amount] parts[$parts]: ") {
            Splitter.splitByWeights(amount, Seq.fill(parts)(1L)) must equal(Splitter.splitEvenly(amount, parts))
          }
        }
      }
    }

    "reject weights that cannot state a proportion" in {
      an[AssertionError] must be thrownBy Splitter.splitByWeights(100, Nil)
      an[AssertionError] must be thrownBy Splitter.splitByWeights(100, Seq(1L, -1L))
      an[AssertionError] must be thrownBy Splitter.splitByWeights(100, Seq(Long.MaxValue, 1L))
    }
  }
}
