package com.mbryzek.util

object Booleans {

  private val TrueValues = Seq("t", "true", "yes", "y")
  private val FalseValues = Seq("f", "false", "no", "n")

  def parse(value: String): Option[Boolean] = {
    val v = value.strip().trim.toLowerCase()
    if (TrueValues.contains(v)) {
      Some(true)

    } else if (FalseValues.contains(v)) {
      Some(false)

    } else {
      None
    }
  }

}
