package com.bryzek.util

import java.security.SecureRandom

case class Random() {

  private val sr = new SecureRandom()
  private val Ambiguous: Seq[String] = "B8G6I1l0OoQDS5Z2".split("").toSeq

  private case class CharSet(characters: Seq[String]) {
    val nonAmbiguous: Seq[String] = characters.filterNot(Ambiguous.contains)
  }

  private val Numbers: CharSet = CharSet(('0' to '9').map(_.toString))
  private val UppercaseLetters: CharSet = CharSet(('A' to 'Z').map(_.toString))
  private val Letters: CharSet = CharSet(
    UppercaseLetters.characters ++ UppercaseLetters.characters.map(_.toLowerCase())
  )
  private val LettersAndNumbers: CharSet = CharSet(Numbers.characters ++ Letters.characters)

  /** Generate an alphanumeric string of a given length. Guaranteed to start with a letter to avoid issues converting to
    * Excel (eg avoid leading z, avoid excel thinking this is a number)
    * @param length
    *   >= 1
    */
  def alphaNumeric(length: Int): String = {
    if (length == 1) {
      gen(Letters.characters, 1)
    } else {
      gen(Letters.characters, 1) + gen(LettersAndNumbers.characters, length - 1)
    }
  }

  def long(): Long = sr.nextLong()

  def positiveLong(): Long = {
    val n = long()
    if (n == 0) {
      positiveLong()
    } else if (n < 0) {
      0 - n
    } else {
      n
    }
  }

  def int(): Int = sr.nextInt()

  def positiveInt(): Int = {
    val n = int()
    if (n == 0) {
      positiveInt()
    } else if (n < 0) {
      0 - n
    } else {
      n
    }
  }

  def nonAmbiguousAlphaUpper(length: Int): String = gen(UppercaseLetters.nonAmbiguous, length)
  def nonAmbiguousNumber(length: Int): String = gen(Numbers.nonAmbiguous, length)

  /** First character is guaranteed to be a letter, the rest are letters or numbers
    */
  def nonAmbiguous(length: Int): String = {
    assert(length >= 1, s"Length '$length' must be >= 1'")
    val first = nonAmbiguousAlphaUpper(1)
    if (length == 1) {
      first
    } else {
      first + gen(LettersAndNumbers.nonAmbiguous, length - 1)
    }
  }

  private def gen(candidates: Seq[String], length: Int): String = {
    assert(length >= 1, s"Length '$length' must be >= 1'")

    def nextChar = candidates(sr.nextInt(candidates.length))

    nextChar + 2.to(length).map { _ => nextChar }.mkString("")
  }
}
