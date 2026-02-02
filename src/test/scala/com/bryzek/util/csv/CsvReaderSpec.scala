package com.bryzek.util.csv

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CsvReaderSpec extends AnyWordSpec with Matchers {

  "CsvReader.fromString" should {
    "parse valid CSV content" in {
      val csv = """name,email
John,john@example.com
Jane,jane@example.com"""

      val result = CsvReader.fromStringWithDefaultSettings(csv)
      result.isValid mustBe true

      val reader = result.toOption.get
      reader.headers() mustBe Seq("name", "email")
    }

    "return error for empty content" in {
      val result = CsvReader.fromStringWithDefaultSettings("")
      result.isInvalid mustBe true
      result.toEither.left.toOption.get.head must include("empty")
    }

    "normalize headers" in {
      val csv = """First Name,Email Address
John,john@example.com"""

      val reader = CsvReader.fromStringWithDefaultSettings(csv).toOption.get
      reader.headers() mustBe Seq("first_name", "email_address")
    }

    "process rows with callback" in {
      val csv = """name,email
John,john@example.com
Jane,jane@example.com"""

      val reader = CsvReader.fromStringWithDefaultSettings(csv).toOption.get
      var rowCount = 0
      val result = reader.read { (builder, row) =>
        val _ = row.getOptionalString("name") // Use row to avoid warning
        rowCount += 1
        builder.withSuccess
      }
      rowCount mustBe 2
      result.errors mustBe empty
      result.numberSuccessful mustBe 2
    }

    "skip empty lines" in {
      val csv = """name,email
John,john@example.com

Jane,jane@example.com"""

      val reader = CsvReader.fromStringWithDefaultSettings(csv).toOption.get
      var rowCount = 0
      reader.read { (builder, row) =>
        val _ = row.getOptionalString("name") // Use row to avoid warning
        rowCount += 1
        builder
      }
      rowCount mustBe 2
    }

    "handle quoted fields with commas" in {
      val csv = """name,notes
"Smith, John","Great player, very skilled""""

      val reader = CsvReader.fromStringWithDefaultSettings(csv).toOption.get
      var capturedRow: Option[CsvRow] = None
      reader.read { (builder, row) =>
        capturedRow = Some(row)
        builder
      }

      capturedRow.get.getOptionalString("name").toOption.get mustBe Some("Smith, John")
      capturedRow.get.getOptionalString("notes").toOption.get mustBe Some("Great player, very skilled")
    }
  }
}
