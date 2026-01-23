package com.bryzek.util.csv

case class CsvSettings(
  valueFormatter: String => String,
  headerFormatter: String => String
)

object CsvSettingDefaults {

  val Default: CsvSettings = CsvSettings(
    valueFormatter = CsvValueFormatter.TrimWhiteSpace,
    headerFormatter = CsvHeaderFormatter.Normalize
  )

}

object CsvValueFormatter {
  val TrimWhiteSpace: String => String = value => value.trim
}

object CsvHeaderFormatter {

  // Pass through header as written
  val Identity: String => String = (value: String) => value

  private val NormalizeRegexp = """([^0-9a-z])""".r
  private val RemoveDuplicateUnderscoresRegexp = """\_(\_+)""".r

  // Lowercase; any letter that is not a-z, 0-9 turned into "_".
  // No leading/trailing underscores
  // No duplicate underscores
  val Normalize: String => String = (value: String) => {
    RemoveDuplicateUnderscoresRegexp.replaceAllIn(
      NormalizeRegexp.replaceAllIn(value.toLowerCase.trim, _ => "_"),
      _ => "_"
    )
  }

}
