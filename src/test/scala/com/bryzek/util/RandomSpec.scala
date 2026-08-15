package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RandomSpec extends AnyWordSpec with Matchers {

  private val random: Random = Random()

  private def multiCount[T](f: Random => T): Int = {
    multi(f).distinct.length
  }
  private def multi[T](f: Random => T): Seq[T] = {
    0.to(99).map { _ => f(random) }
  }

  "alphanumeric" in {
    random.alphaNumeric(10).length mustBe 10
    multiCount(_.alphaNumeric(10)) mustBe 100
  }

  "long" in {
    multiCount(_.long()) mustBe 100
  }

  "positiveLong" in {
    val all = multi(_.positiveLong())
    all.length mustBe 100
    all.filter(_ <= 0) mustBe Nil
  }

  "int" in {
    multiCount(_.int()) mustBe 100
  }

  "positiveInt" in {
    val all = multi(_.positiveInt())
    all.length mustBe 100
    all.filter(_ <= 0) mustBe Nil
  }

  "nonAmbiguousAlphaUpper" in {
    multiCount(_.nonAmbiguousAlphaUpper(10)) mustBe 100
  }

  "nonAmbiguousNumber" in {
    multiCount(_.nonAmbiguousNumber(10)) mustBe 100
  }

  "nonAmbiguous" in {
    val amb = "B8G6I1l0OoQDS5Z2".split("").toSeq
    multiCount(_.nonAmbiguous(10)) mustBe 100
    random.nonAmbiguous(1000).split("").filter(amb.contains).toSeq mustBe Nil
  }

  "nonAmbiguousLower" should {

    // 28 * 1000, so every character is expected 1000 times below.
    val sample: Seq[String] = random.nonAmbiguousLower(28000).split("").toSeq

    "generate a string of the requested length" in {
      random.nonAmbiguousLower(10).length mustBe 10
      random.nonAmbiguousLower(1).length mustBe 1
      multiCount(_.nonAmbiguousLower(10)) mustBe 100
    }

    "reject a length below 1" in {
      an[AssertionError] must be thrownBy random.nonAmbiguousLower(0)
    }

    /** Pinned as a literal so that a change to `Random.Ambiguous` -- which is private, and which
      * `properties.db.TeamInviteCode` in platform keeps its own copy of -- is a failing test here rather than a
      * silently different token in every consumer.
      */
    "draw from exactly the 28 non-ambiguous lowercase characters" in {
      sample.distinct.sorted.mkString mustBe "3479abcdefghijkmnpqrstuvwxyz"
    }

    /** The regression this method exists for (ISS-2789): `nonAmbiguous(n).toLowerCase` reaches 29 characters, not 28.
      * `L` is non-ambiguous and `l` is not, so the fold emits the one lowercase letter the filter excludes -- against
      * `1` and `I`, which is the confusion the filter is there to prevent.
      */
    "never emit an ambiguous character" in {
      val amb = "B8G6I1l0OoQDS5Z2".split("").toSet
      sample.filter(amb.contains) mustBe Nil
    }

    "never emit an uppercase character" in {
      sample.filter(c => c != c.toLowerCase) mustBe Nil
    }

    /** The other half of that regression, and the half that is hard to see: folding a mixed-case draw to one case
      * leaves 17 of the 29 reachable characters at TWICE the weight of the other 12, because they are reachable from
      * both cases. The bound is +/-20% of the expected 1000, which is ~6.4 standard deviations for a uniform draw and
      * so is not flaky, while the fold's own frequencies (~1217 and ~609) both sit outside it.
      */
    "draw uniformly" in {
      val counts = sample.groupBy(identity).view.mapValues(_.length)
      counts.filter { case (_, n) => n < 800 || n > 1200 }.toMap mustBe Map.empty
    }
  }
}
