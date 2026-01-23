package com.bryzek.util.csv

import scala.annotation.tailrec

/** Skips empty lines after formatting, and keeps track of the actual line number
  */
case class NonEmptyIterator(settings: CsvSettings, source: Iterator[Seq[String]]) extends Iterator[Seq[String]] {
  private var i: Int = 0
  private var n: Option[Seq[String]] = nextNonEmptyLine()

  override def hasNext: Boolean = n.nonEmpty

  override def next(): Seq[String] = {
    val value = n.getOrElse {
      sys.error("Iterator exhausted")
    }
    n = nextNonEmptyLine()
    value
  }

  def lineNumber: Int = i

  @tailrec
  private def nextNonEmptyLine(): Option[Seq[String]] = {
    if (source.hasNext) {
      i += 1
      val all = source.next().map(settings.valueFormatter)
      if (!all.exists(_.nonEmpty)) {
        nextNonEmptyLine()
      } else {
        Some(all)
      }
    } else {
      None
    }
  }
}
