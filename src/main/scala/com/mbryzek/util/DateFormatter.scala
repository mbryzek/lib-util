package com.mbryzek.util

import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}
import org.joda.time.{DateTime, LocalDate}

import scala.annotation.tailrec

object DateFormatter {

  private val mmDdYYYY: DateTimeFormatter = DateTimeFormat.forPattern("MM/dd/yyyy")
  private val monYYYY: DateTimeFormatter = DateTimeFormat.forPattern("MMM yyyy")
  private val textDateFormat = DateTimeFormat.forPattern("MMM d, yyyy")
  private val yyyyMmDd = DateTimeFormat.forPattern("yyyy-MM-dd")

  private val longDateFormatter = DateTimeFormat.forPattern("EEEE, MMMM d, yyyy")
  private val timeFormatter = DateTimeFormat.forPattern("h:mm a")

  def longDateAndTime(timestamp: DateTime): String = {
    longDateFormatter.print(timestamp) + ", " + timeFormatter.print(timestamp)
  }

  def short(timestamp: DateTime): String = short(timestamp.toLocalDate)
  def short(timestamp: LocalDate): String = {
    format(mmDdYYYY.print(timestamp))
  }

  def monthYear(timestamp: DateTime): String = monthYear(timestamp.toLocalDate)
  def monthYear(timestamp: LocalDate): String = {
    format(monYYYY.print(timestamp))
  }

  def quarterYear(timestamp: DateTime): String = quarterYear(timestamp.toLocalDate)
  def quarterYear(timestamp: LocalDate): String = {
    val q = DateUtil.toQuarter(timestamp)
    s"Q${q.number} ${q.year}"
  }

  def yyyyMmDd(timestamp: DateTime): String = yyyyMmDd(timestamp.toLocalDate)
  def yyyyMmDd(timestamp: LocalDate): String = {
    format(yyyyMmDd.print(timestamp))
  }

  def shortText(timestamp: DateTime): String = shortText(timestamp.toLocalDate)
  def shortText(timestamp: LocalDate): String = {
    format(textDateFormat.print(timestamp))
  }

  private def format(d: String): String = {
    d.split("/").map(stripLeadingZeroes).mkString("/")
  }

  @tailrec
  private def stripLeadingZeroes(value: String): String = {
    if (value.startsWith("0")) {
      stripLeadingZeroes(value.drop(1))
    } else {
      value
    }
  }
}
