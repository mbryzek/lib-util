package com.bryzek.util

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.joda.time.DateTimeZone

class DefaultTimezoneSpec extends AnyWordSpec with Matchers {

  "DefaultTimezone" must {
    "create valid timezone with correct properties" in {
      val tz = DefaultTimezone("Eastern", "America/New_York")
      tz.name mustBe "Eastern"
      tz.code mustBe "America/New_York"
      tz.dateTimeZone mustBe DateTimeZone.forID("America/New_York")
    }
  }

  "DefaultTimezones" must {
    "provide predefined timezones" in {
      DefaultTimezones.AmericaNewYork.name mustBe "Eastern"
      DefaultTimezones.AmericaNewYork.code mustBe "America/New_York"
      DefaultTimezones.Default mustBe DefaultTimezones.AmericaNewYork
    }

    "include all timezones in the all list" in {
      DefaultTimezones.all mustBe List(DefaultTimezones.AmericaNewYork)
    }

    "fromString" must {
      "return timezone for valid codes" in {
        DefaultTimezones.fromString("America/New_York") mustBe Some(DefaultTimezones.AmericaNewYork)
        DefaultTimezones.fromString("america/new_york") mustBe Some(DefaultTimezones.AmericaNewYork)
        DefaultTimezones.fromString("AMERICA/NEW_YORK") mustBe Some(DefaultTimezones.AmericaNewYork)
      }

      "return None for invalid codes" in {
        DefaultTimezones.fromString("Invalid/Timezone") mustBe None
        DefaultTimezones.fromString("") mustBe None
        DefaultTimezones.fromString("Europe/London") mustBe None
      }
    }

    "validate" must {
      "return valid for correct timezone codes" in {
        val result = DefaultTimezones.validate("America/New_York")
        result.isValid mustBe true
        result.toOption mustBe Some(DefaultTimezones.AmericaNewYork)
      }

      "return valid for case insensitive codes" in {
        val result = DefaultTimezones.validate("america/new_york")
        result.isValid mustBe true
        result.toOption mustBe Some(DefaultTimezones.AmericaNewYork)
      }

      "handle whitespace in codes" in {
        val result = DefaultTimezones.validate("  America/New_York  ")
        result.isValid mustBe true
        result.toOption mustBe Some(DefaultTimezones.AmericaNewYork)
      }

      "return invalid for incorrect timezone codes" in {
        val result = DefaultTimezones.validate("Invalid/Timezone")
        result.isInvalid mustBe true
        result.swap.toOption.get.head must include("Invalid timezone 'Invalid/Timezone'")
        result.swap.toOption.get.head must include("Must be one of: America/New_York")
      }

      "return invalid for empty codes" in {
        val result = DefaultTimezones.validate("")
        result.isInvalid mustBe true
        result.swap.toOption.get.head must include("Invalid timezone ''")
      }
    }
  }

}
