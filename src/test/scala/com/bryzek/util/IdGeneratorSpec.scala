package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class IdGeneratorSpec extends AnyWordSpec with Matchers {

  "prefix" in {
    IdGenerator("tst").randomId().startsWith("tst-") mustBe true
    IdGenerator("foo").randomId().startsWith("foo-") mustBe true
  }

  "unique" in {
    val gen = IdGenerator("tst")
    0.to(99).map { _ => gen.randomId() }.distinct.length mustBe 100
  }

}
