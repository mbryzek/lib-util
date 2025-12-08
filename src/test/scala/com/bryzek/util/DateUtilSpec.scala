package com.bryzek.util

import org.joda.time.LocalDate
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DateUtilSpec extends AnyWordSpec with Matchers {

  "toQuarter" in {
    def t(date: String) = DateUtil.toQuarter(LocalDate.parse(date))

    t("2024-01-01") mustBe DateUtil.Quarter.Q1(2024)
    t("2025-01-01") mustBe DateUtil.Quarter.Q1(2025)
    t("2025-01-31") mustBe DateUtil.Quarter.Q1(2025)
    t("2025-02-01") mustBe DateUtil.Quarter.Q1(2025)
    t("2025-03-01") mustBe DateUtil.Quarter.Q1(2025)
    t("2025-03-31") mustBe DateUtil.Quarter.Q1(2025)
    t("2025-04-01") mustBe DateUtil.Quarter.Q2(2025)
    t("2025-05-08") mustBe DateUtil.Quarter.Q2(2025)
    t("2025-06-08") mustBe DateUtil.Quarter.Q2(2025)
    t("2025-07-08") mustBe DateUtil.Quarter.Q3(2025)
    t("2025-08-08") mustBe DateUtil.Quarter.Q3(2025)
    t("2025-09-08") mustBe DateUtil.Quarter.Q3(2025)
    t("2025-10-08") mustBe DateUtil.Quarter.Q4(2025)
    t("2025-11-08") mustBe DateUtil.Quarter.Q4(2025)
    t("2025-12-08") mustBe DateUtil.Quarter.Q4(2025)
    t("2025-12-31") mustBe DateUtil.Quarter.Q4(2025)
  }

  "toQuarterStart" in {
    def t(date: String) = DateUtil.toQuarterStart(LocalDate.parse(date))

    t("2025-01-01") mustBe LocalDate.parse("2025-01-01")
    t("2025-01-31") mustBe LocalDate.parse("2025-01-01")
    t("2025-02-01") mustBe LocalDate.parse("2025-01-01")
    t("2025-03-01") mustBe LocalDate.parse("2025-01-01")
    t("2025-03-31") mustBe LocalDate.parse("2025-01-01")
    t("2025-04-01") mustBe LocalDate.parse("2025-04-01")
    t("2025-05-08") mustBe LocalDate.parse("2025-04-01")
    t("2025-06-08") mustBe LocalDate.parse("2025-04-01")
    t("2025-07-08") mustBe LocalDate.parse("2025-07-01")
    t("2025-08-08") mustBe LocalDate.parse("2025-07-01")
    t("2025-09-08") mustBe LocalDate.parse("2025-07-01")
    t("2025-10-08") mustBe LocalDate.parse("2025-10-01")
    t("2025-11-08") mustBe LocalDate.parse("2025-10-01")
    t("2025-12-08") mustBe LocalDate.parse("2025-10-01")
    t("2025-12-31") mustBe LocalDate.parse("2025-10-01")
  }

}
