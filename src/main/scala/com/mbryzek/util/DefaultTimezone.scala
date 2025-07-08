package com.mbryzek.util

import cats.data.ValidatedNec
import cats.implicits.*
import org.joda.time.DateTimeZone

case class DefaultTimezone(name: String, code: String) {
  val dateTimeZone: DateTimeZone = DateTimeZone.forID(code)
}

object DefaultTimezones {
  val AmericaNewYork: DefaultTimezone = DefaultTimezone("Eastern", "America/New_York")

  val Default: DefaultTimezone = AmericaNewYork

  val all = List(AmericaNewYork)
  private val byCode: Map[String, DefaultTimezone] = all.map(x => x.code.toLowerCase -> x).toMap
  def fromString(value: String): Option[DefaultTimezone] = byCode.get(value.toLowerCase)

  def validate(code: String): ValidatedNec[String, DefaultTimezone] = {
    fromString(code.trim.toLowerCase()) match {
      case Some(tz) => tz.validNec
      case None =>
        (
          s"Invalid timezone '$code'. Must be one of: " + all.map(_.code).mkString(", ")
        ).invalidNec
    }
  }

}
