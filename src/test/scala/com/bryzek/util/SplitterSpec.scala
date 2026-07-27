package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SplitterSpec extends AnyWordSpec with Matchers {

  private def toF(value: String): Fraction =
    Fraction.parse(value).getOrElse(sys.error(s"Invalid fraction[$value]"))

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
}
