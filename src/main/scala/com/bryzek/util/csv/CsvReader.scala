package com.bryzek.util.csv

import cats.data.{NonEmptyChain, ValidatedNec}
import cats.implicits.*
import com.github.tototoshi.csv as tototoshi
import com.github.tototoshi.csv.CSVFormat.defaultCSVFormat

import java.io.{File, StringReader}

object CsvReader {

  def fromStringWithDefaultSettings(content: String): ValidatedNec[String, CsvReader] = {
    fromString(CsvSettingDefaults.Default, content)
  }

  def fromString(settings: CsvSettings, content: String): ValidatedNec[String, CsvReader] = {
    if (content.trim.isEmpty) {
      "CSV content is empty".invalidNec
    } else {
      CsvReader(settings, Left(content)).validNec
    }
  }

  def fromFileWithDefaultSettings(path: String): ValidatedNec[String, CsvReader] = {
    fromFile(CsvSettingDefaults.Default, path)
  }

  def fromFileWithDefaultSettings(file: File): ValidatedNec[String, CsvReader] = {
    fromFile(CsvSettingDefaults.Default, file)
  }

  def fromFile(settings: CsvSettings, path: String): ValidatedNec[String, CsvReader] = {
    fromFile(settings, new File(path))
  }

  def fromFile(settings: CsvSettings, file: File): ValidatedNec[String, CsvReader] = {
    if (!file.exists) {
      s"File '${file.getAbsolutePath}' does not exist".invalidNec
    } else if (!file.isFile) {
      s"File '${file.getAbsolutePath}' is not a file".invalidNec
    } else {
      CsvReader(settings, Right(file)).validNec
    }
  }

}

case class CsvResult(numberSuccessful: Long, errors: Seq[CsvError]) {
  def numberErrors: Int = CsvResult.errorsByLineNumber(errors).size
}

object CsvResult {
  def errorsByLineNumber(errors: Seq[CsvError]): Map[Int, String] = errors.groupBy(_.lineNumber).map { case (l, all) =>
    l -> all.map(_.message).distinct.mkString("; ")
  }
}

case class CsvError(lineNumber: Int, message: String)

case class CsvResultBuilder(
  numberSuccessful: Long,
  headers: Seq[String],
  lineNumber: Int = 2, // line 2 contains the headers
  errors: Seq[CsvError] = Nil
) {

  def withSuccess: CsvResultBuilder = {
    this.copy(numberSuccessful = numberSuccessful + 1)
  }

  def withLineNumber(lineNumber: Int): CsvResultBuilder = {
    this.copy(lineNumber = lineNumber)
  }

  def withError(message: String): CsvResultBuilder = {
    withErrors(Seq(message))
  }

  def withErrors(messages: NonEmptyChain[String]): CsvResultBuilder = {
    withErrors(messages.toList)
  }

  def withErrors(messages: Seq[String]): CsvResultBuilder = {
    this.copy(
      errors = errors ++ messages.map { m => CsvError(lineNumber, m) }
    )
  }

  def build(): CsvResult = {
    CsvResult(
      numberSuccessful = numberSuccessful,
      errors = errors
    )
  }

}

case class CsvReader(
  settings: CsvSettings,
  source: Either[String, File]
) {

  private def createReader(): tototoshi.CSVReader = {
    source match {
      case Left(content) => tototoshi.CSVReader.open(new StringReader(content))(using defaultCSVFormat)
      case Right(file) => tototoshi.CSVReader.open(file)(using defaultCSVFormat)
    }
  }

  def headers(): Seq[String] = {
    val reader = createReader()
    try {
      val it = NonEmptyIterator(settings, reader.iterator)
      readHeaders(it)
    } finally {
      reader.close()
    }
  }

  private def readHeaders(it: NonEmptyIterator): Seq[String] = {
    if (it.hasNext) {
      it.next().map(settings.headerFormatter)
    } else {
      Nil
    }
  }

  def read(eachRow: (CsvResultBuilder, CsvRow) => CsvResultBuilder): CsvResult = {
    val reader = createReader()
    try {
      val it = NonEmptyIterator(settings, reader.iterator)
      val headers = readHeaders(it)

      var builder = CsvResultBuilder(numberSuccessful = 0, headers = headers)
      while (it.hasNext) {
        val line = it.next()
        builder = eachRow(
          builder.withLineNumber(it.lineNumber),
          CsvRow(
            headers.zipWithIndex.map { case (k, i) =>
              k -> line.lift(i).getOrElse("")
            }.toMap
          )
        )
      }
      builder.build()
    } finally {
      reader.close()
    }
  }
}
