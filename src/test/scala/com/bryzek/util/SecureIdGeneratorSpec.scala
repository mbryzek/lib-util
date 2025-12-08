package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SecureIdGeneratorSpec extends AnyWordSpec with Matchers {

  "prefix" in {
    SecureIdGenerator("tst").randomId().startsWith("tst-") mustBe true
    SecureIdGenerator("foo").randomId().startsWith("foo-") mustBe true
  }

  "unique" in {
    val gen = SecureIdGenerator("tst")
    0.to(99).map { _ => gen.randomId() }.distinct.length mustBe 100
  }

}
