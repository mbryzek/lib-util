package com.bryzek.util

import org.joda.time.DateTime
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DateFormatterSpec extends AnyWordSpec with Matchers {

  private val april82021 = DateTime.parse("2021-04-08T10:45:53.165-04:00")
  private val april82021OnTheHour = DateTime.parse("2021-04-08T10:00:00.000-04:00")

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

  "longDateAndTime" must {
    "with non-zero minutes" in {
      DateFormatter.longDateAndTime(april82021) mustBe "Thursday, April 8, 2021 @ 10:45 AM"
    }
    "with zero minutes omits :00" in {
      DateFormatter.longDateAndTime(april82021OnTheHour) mustBe "Thursday, April 8, 2021 @ 10AM"
    }
  }

  "shortDateAndTime" must {
    "current year" in {
      val f = DateFormatter.shortDateAndTime(april82021.withYear(DateTime.now.getYear))
      // Drop the day of week as it changes year to year. Testing mainly that the year is not here
      val parts = f.split("\\s+").toList
      parts.head.length mustBe 3
      parts.drop(1).mkString(" ") mustBe "Apr 8 @ 10:45 AM"
    }
    "other year" in {
      DateFormatter.shortDateAndTime(april82021) mustBe "Thu Apr 8, 2021 @ 10:45 AM"
    }
    "zero minutes omits :00" in {
      DateFormatter.shortDateAndTime(april82021OnTheHour) mustBe "Thu Apr 8, 2021 @ 10AM"
    }
  }
}
