package com.mbryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class Base64UtilSpec extends AnyWordSpec with Matchers {

  "encode/decode" in {
    val value = "test"
    val hash = Base64Util.encode(value)
    hash == value mustBe false
    Base64Util.decode(hash) mustBe value
  }

}
