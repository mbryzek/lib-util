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

case class CsvResult(errors: Seq[String])

case class CsvError(lineNumber: Int, message: String)

case class CsvResultBuilder(
  headers: Seq[String],
  lineNumber: Int = 2,
  errors: Seq[CsvError] = Nil
) {

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
      errors = errors.map { e =>
        s"Line ${e.lineNumber}: ${e.message}"
      }
    )
  }

}

case class CsvReader(
  settings: CsvSettings,
  source: Either[String, File]
) {

  private def createIterator(): NonEmptyIterator = {
    val reader = source match {
      case Left(content) => tototoshi.CSVReader.open(new StringReader(content))(using defaultCSVFormat)
      case Right(file) => tototoshi.CSVReader.open(file)(using defaultCSVFormat)
    }
    NonEmptyIterator(settings, reader.iterator)
  }

  def headers(): Seq[String] = {
    val it = createIterator()
    readHeaders(it)
  }

  private def readHeaders(it: NonEmptyIterator): Seq[String] = {
    if (it.hasNext) {
      it.next().map(settings.headerFormatter)
    } else {
      Nil
    }
  }

  def read(eachRow: (CsvResultBuilder, CsvRow) => CsvResultBuilder): CsvResult = {
    val it = createIterator()
    val headers = readHeaders(it)

    var builder = CsvResultBuilder(headers = headers)
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
  }
}
