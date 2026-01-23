package com.bryzek.util.csv

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CsvRowSpec extends AnyWordSpec with Matchers {

  "CsvRow" should {
    "get optional string" in {
      val row = CsvRow(Map("name" -> "John", "email" -> ""))
      row.getOptionalString("name").toOption.get mustBe Some("John")
      row.getOptionalString("email").toOption.get mustBe None
      row.getOptionalString("missing").toOption.get mustBe None
    }

    "get required string" in {
      val row = CsvRow(Map("name" -> "John", "email" -> ""))
      row.getRequiredString("name").toOption.get mustBe "John"
      row.getRequiredString("email").isInvalid mustBe true
      row.getRequiredString("missing").isInvalid mustBe true
    }

    "get optional int with bounds" in {
      val row = CsvRow(Map("rating" -> "4", "empty" -> ""))
      row.getOptionalInt("rating").toOption.get mustBe Some(4)
      row.getOptionalInt("empty").toOption.get mustBe None
      row.getOptionalInt("rating", minimum = Some(1), maximum = Some(5)).toOption.get mustBe Some(4)
      row.getOptionalInt("rating", minimum = Some(5)).isInvalid mustBe true
      row.getOptionalInt("rating", maximum = Some(3)).isInvalid mustBe true
    }

    "get optional boolean" in {
      val row = CsvRow(Map("yes" -> "true", "no" -> "false", "y" -> "yes", "n" -> "no", "empty" -> ""))
      row.getOptionalBoolean("yes").toOption.get mustBe Some(true)
      row.getOptionalBoolean("no").toOption.get mustBe Some(false)
      row.getOptionalBoolean("y").toOption.get mustBe Some(true)
      row.getOptionalBoolean("n").toOption.get mustBe Some(false)
      row.getOptionalBoolean("empty").toOption.get mustBe None
    }

    "normalize field names" in {
      val row = CsvRow(Map("First Name" -> "John", "EMAIL" -> "john@example.com"))
      row.getOptionalString("first name").toOption.get mustBe Some("John")
      row.getOptionalString("email").toOption.get mustBe Some("john@example.com")
    }
  }
}
