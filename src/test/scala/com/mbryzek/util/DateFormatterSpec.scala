package com.mbryzek.util

import org.joda.time.DateTime
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DateFormatterSpec extends AnyWordSpec with Matchers {

  private val april82021 = DateTime.parse("2021-04-08T10:45:53.165-04:00")

  "short" must {
    "strip zeros" in {
      DateFormatter.short(april82021) mustBe "4/8/2021"
    }
    "preserve non zeros" in {
      DateFormatter.short(DateTime.parse("2021-11-23T10:45:53.165-04:00")) mustBe "11/23/2021"
    }
  }

  "quarterYear" in {
    DateFormatter.quarterYear(april82021) mustBe "Q2 2021"
  }

  "monthYear" in {
    DateFormatter.monthYear(april82021) mustBe "Apr 2021"
  }
}
