package com.bryzek.util.csv

import cats.data.ValidatedNec
import cats.implicits.*
import com.bryzek.util.Booleans
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTime, LocalDate}

import scala.util.{Failure, Success, Try}

/** Represents a single row from a CSV file, providing helper functions to validate the type of data
  */
case class CsvRow(original: Map[String, String]) {
  private type ValidationResult[A] = ValidatedNec[String, A]

  private def identityFormatter(value: String) = value

  private def formatKey(value: String): String = formatValue(value).toLowerCase()
  private def formatValue(value: String): String = value.replaceAll("\\s+", " ").trim

  private val data = original.map { case (k, v) => formatKey(k) -> formatValue(v) }

  def get(field: String): ValidatedNec[String, Option[String]] = _getOptionalString(field).validNec

  def getOptionalString(field: String)(implicit
    formatter: String => String = identityFormatter
  ): ValidatedNec[String, Option[String]] = {
    _getOptionalString(field)(using formatter).validNec
  }

  private def _getOptionalString(field: String)(implicit
    formatter: String => String
  ): Option[String] = {
    data.get(formatKey(field)).map(_.trim).map(formatter) match {
      case Some(v) if v.nonEmpty => Some(v)
      case _ => None
    }
  }

  def getRequiredString(field: String)(implicit
    formatter: String => String = identityFormatter
  ): ValidationResult[String] = {
    _getOptionalString(field) match {
      case None =>
        if (data.isDefinedAt(formatKey(field))) {
          errorMessage(field, "cannot be blank").invalidNec
        } else {
          errorMessage(field, "is missing").invalidNec
        }

      case Some(v) => v.validNec
    }
  }

  def getOptionalInt(
    field: String,
    minimum: Option[Int] = None,
    maximum: Option[Int] = None
  ): ValidationResult[Option[Int]] = {
    getOptionalString(field).andThen {
      case None => None.validNec
      case Some(original) => parseInt(field, original, minimum, maximum).map(Some(_))
    }
  }

  def getRequiredInt(
    field: String,
    minimum: Option[Int] = None,
    maximum: Option[Int] = None
  ): ValidationResult[Int] = {
    getRequiredString(field).andThen { v =>
      parseInt(field, v, minimum, maximum)
    }
  }

  def getOptionalLong(
    field: String,
    minimum: Option[Long] = None
  )(implicit
    formatter: String => String = identityFormatter
  ): ValidationResult[Option[Long]] = {
    getOptionalString(field)(using formatter).andThen {
      case None => None.validNec
      case Some(original) => parseLong(field, original, minimum).map(Some(_))
    }
  }

  def getRequiredLong(
    field: String,
    minimum: Option[Long] = None
  )(implicit
    formatter: String => String = identityFormatter
  ): ValidationResult[Long] = {
    getRequiredString(field).andThen { v =>
      parseLong(field, v, minimum)
    }
  }

  def getOptionalBigDecimal(
    field: String,
    minimum: Option[Long] = None
  )(implicit
    formatter: String => String = identityFormatter
  ): ValidationResult[Option[BigDecimal]] = {
    _getOptionalString(field)(using formatter) match {
      case None => None.validNec
      case Some(v) => toBigDecimal(field, v, minimum).map { v => Some(v) }
    }
  }

  def getRequiredBigDecimal(
    field: String,
    minimum: Option[Long] = None
  )(implicit
    formatter: String => String = identityFormatter
  ): ValidationResult[BigDecimal] = {
    getRequiredString(field)(using formatter).andThen { value =>
      toBigDecimal(field, value, minimum)
    }
  }

  def getOptionalLocalDate(
    field: String
  )(implicit
    toLocalDate: String => LocalDate = ISODateTimeFormat.yearMonthDay.parseLocalDate
  ): ValidationResult[Option[LocalDate]] = {
    _getOptionalString(field) match {
      case None => None.validNec
      case Some(value) => parseLocalDate(field, value)(toLocalDate).map(d => Some(d))
    }
  }

  def getRequiredLocalDate(
    field: String
  )(implicit
    toLocalDate: String => LocalDate = ISODateTimeFormat.yearMonthDay.parseLocalDate
  ): ValidationResult[LocalDate] = {
    getRequiredString(field).andThen { value =>
      parseLocalDate(field, value)(toLocalDate)
    }
  }

  def getOptionalLocalDateMMDDYYYY(field: String): ValidationResult[Option[LocalDate]] = {
    getOptionalLocalDate(field)(using toLocalDateFromMMDDYYY)
  }

  def getRequiredLocalDateMMDDYYYY(field: String): ValidationResult[LocalDate] = {
    getRequiredLocalDate(field)(using toLocalDateFromMMDDYYY)
  }

  private def toLocalDateFromMMDDYYY(value: String): LocalDate = {
    value.trim.split("\\/").toList.map(_.trim) match {
      case month :: day :: year :: Nil =>
        val y = if (year.forall(_.isDigit)) {
          maybePrependCentury(year.toInt)
        } else {
          year
        }
        ISODateTimeFormat.yearMonthDay.parseLocalDate(s"$y-$month-$day")
      case _ => sys.error(s"Expected mm/dd/yyyy but found ${value.trim}")
    }
  }

  /** Parses year taking assumption that any missing digits come from current year. Example: In the year 2020, a value
    * of '34' would be interpreted as '2034'
    */
  private def maybePrependCentury(year: Int): Int = {
    val current = LocalDate.now.getYear
    if (year.toString.length < current.toString.length) {
      (current.toString.dropRight(current.toString.length - year.toString.length) + year.toString).toInt
    } else {
      year
    }
  }

  def getOptionalBoolean(field: String): ValidationResult[Option[Boolean]] = {
    _getOptionalString(field) match {
      case None => None.validNec
      case Some(v) => validateBoolean(field, v).map(b => Some(b))
    }
  }

  def getRequiredBoolean(field: String): ValidationResult[Boolean] = {
    getRequiredString(field).andThen { value =>
      validateBoolean(field, value)
    }
  }

  private def validateBoolean(field: String, value: String): ValidationResult[Boolean] = {
    Booleans.parse(value) match {
      case None => errorMessage(field, value, "is not a valid boolean").invalidNec
      case Some(v) => v.validNec
    }
  }

  private def parseLocalDate(field: String, value: String)(
    toLocalDate: String => LocalDate
  ): ValidationResult[LocalDate] = {
    Try {
      toLocalDate(value)
    } match {
      case Success(v) => v.validNec
      case Failure(_) => {
        errorMessage(
          field,
          value,
          s"needs to be a valid date, e.g. ${ISODateTimeFormat.yearMonthDay.print(LocalDate.now)}"
        ).invalidNec
      }
    }
  }

  def getOptionalDateTime(
    field: String
  )(implicit
    toDateTime: String => DateTime = ISODateTimeFormat.dateTime.parseDateTime
  ): ValidationResult[Option[DateTime]] = {
    _getOptionalString(field) match {
      case None => None.validNec
      case Some(v) => parseDateTime(field, v)(toDateTime).map { v => Some(v) }
    }
  }

  def getRequiredDateTime(
    field: String
  )(implicit
    toDateTime: String => DateTime = ISODateTimeFormat.dateTime.parseDateTime
  ): ValidationResult[DateTime] = {
    getRequiredString(field).andThen { v =>
      parseDateTime(field, v)(toDateTime)
    }
  }

  private def toBigDecimal(
    field: String,
    value: String,
    minimum: Option[Long]
  ): ValidationResult[BigDecimal] = {
    Try {
      BigDecimal(value)
    } match {
      case Failure(_) => errorMessage(field, value, "needs to be a 'decimal'").invalidNec
      case Success(v) => {
        minimum match {
          case Some(min) if v < min => minErrorMessage(field, value, min).invalidNec
          case _ => v.validNec
        }
      }
    }
  }

  private def parseDateTime(field: String, value: String)(
    toDateTime: String => DateTime
  ): ValidationResult[DateTime] = {
    Try {
      toDateTime(value)
    } match {
      case Success(ts) => ts.validNec
      case Failure(_) => {
        errorMessage(
          field,
          value,
          s"needs to be a valid date time, e.g. ${ISODateTimeFormat.dateTime.print(DateTime.now)}"
        ).invalidNec
      }
    }
  }

  private def parseInt(
    field: String,
    originalValue: String,
    minimum: Option[Int],
    maximum: Option[Int]
  ): ValidationResult[Int] = {
    originalValue.toIntOption match {
      case None => errorMessage(field, originalValue, "is not a valid integer").invalidNec
      case Some(v) =>
        (minimum, maximum) match {
          case (Some(min), _) if v < min => errorMessage(field, originalValue, s"must be >= $min").invalidNec
          case (_, Some(max)) if v > max => errorMessage(field, originalValue, s"must be <= $max").invalidNec
          case _ => v.validNec
        }
    }
  }

  private def parseLong(
    field: String,
    originalValue: String,
    minimum: Option[Long]
  ): ValidationResult[Long] = {
    originalValue.toLongOption match {
      case None => errorMessage(field, originalValue, "is not a valid 'long'").invalidNec
      case Some(v) => {
        minimum match {
          case Some(min) if v < min => minErrorMessage(field, originalValue, min).invalidNec
          case _ => v.validNec
        }
      }
    }
  }

  private def errorMessage(field: String, value: String, msg: String): String = {
    errorMessage(field, s"value '${shorten(value)}' $msg")
  }

  private def errorMessage(field: String, msg: String): String = {
    s"Field '$field' $msg"
  }

  private def minErrorMessage(field: String, value: String, min: Long): String = {
    errorMessage(field, value, s"must be >= $min")
  }

  private def shorten(value: String, n: Int = 10): String = {
    if (value.length > n) {
      value.take(n) + "..."
    } else {
      value
    }
  }

}
