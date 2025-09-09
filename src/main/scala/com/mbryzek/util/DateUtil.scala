package com.mbryzek.util

import org.joda.time.LocalDate
import org.joda.time.DateTimeConstants

object DateUtil {
  sealed trait Quarter {
    def number: Int
    def year: Int
  }

  object Quarter {
    case class Q1(year: Int) extends Quarter { override def number = 1 }
    case class Q2(year: Int) extends Quarter { override def number = 2 }
    case class Q3(year: Int) extends Quarter { override def number = 3 }
    case class Q4(year: Int) extends Quarter { override def number = 4 }
  }

  def toQuarterStart(d: LocalDate): LocalDate = {
    val q = toQuarter(d)
    def build(month: Int): LocalDate = LocalDate(q.year, month, 1)

    import DateTimeConstants._
    q match {
      case _: Quarter.Q1 => build(JANUARY)
      case _: Quarter.Q2 => build(APRIL)
      case _: Quarter.Q3 => build(JULY)
      case _: Quarter.Q4 => build(OCTOBER)
    }
  }

  def toQuarter(d: LocalDate): Quarter = {
    val year = d.getYear

    import DateTimeConstants._
    d.getMonthOfYear match {
      case JANUARY => Quarter.Q1(year)
      case FEBRUARY => Quarter.Q1(year)
      case MARCH => Quarter.Q1(year)
      case APRIL => Quarter.Q2(year)
      case MAY => Quarter.Q2(year)
      case JUNE => Quarter.Q2(year)
      case JULY => Quarter.Q3(year)
      case AUGUST => Quarter.Q3(year)
      case SEPTEMBER => Quarter.Q3(year)
      case OCTOBER => Quarter.Q4(year)
      case NOVEMBER => Quarter.Q4(year)
      case DECEMBER => Quarter.Q4(year)
      case other => sys.error(s"Invalid month: $other")
    }
  }

}
