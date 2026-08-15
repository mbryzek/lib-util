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
  private val LowercaseLetters: CharSet = CharSet(('a' to 'z').map(_.toString))
  private val Letters: CharSet = CharSet(UppercaseLetters.characters ++ LowercaseLetters.characters)
  private val LettersAndNumbers: CharSet = CharSet(Numbers.characters ++ Letters.characters)
  private val LowercaseLettersAndNumbers: CharSet = CharSet(Numbers.characters ++ LowercaseLetters.characters)

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

  /** Uniform over the 28 non-ambiguous LOWERCASE characters: a-z without `l` and `o`, 0-9 without `0 1 2 5 6 8`.
    *
    * For a caller whose value is stored or compared case-insensitively -- a URL token, a code typed back into a form,
    * an id in a lowercase column. Such a caller wrote `nonAmbiguous(n).toLowerCase` before this existed, and that is
    * not the same string in three ways (ISS-2789).
    *
    * It is SMALLER than it reads. [[nonAmbiguous]] draws the first character from the 18 non-ambiguous uppercase
    * letters and the rest from a 46-character MIXED-case set, so a 6-character value looks like one of 18 * 46^5 = 3.7
    * billion; folding it to one case lands it on 29 distinct characters, which is at most 594 million -- roughly 9x
    * less than the length implies, all of it generated and immediately thrown away.
    *
    * It is NOT UNIFORM, which is the part that is hard to notice, because the length and the alphabet both still look
    * right. 17 of those 29 characters are reachable from both cases and so arrive at twice the weight of the other 12.
    *
    * And it reintroduces the very ambiguity the name promises. `L` is non-ambiguous and `l` is not, so the fold emits
    * the one lowercase letter this set excludes -- against `1` and `I`, which is the confusion the filter exists for.
    *
    * The first character is NOT forced to be a letter, which [[nonAmbiguous]] does "to avoid issues converting to
    * Excel". That is a property of where a value is going, not of the alphabet: a caller that needs it prefixes the
    * value (which every caller of this in the platform does today) or calls [[nonAmbiguous]] instead. Forcing it here
    * would also cost the uniformity that is the point -- the first position would draw from 24 characters and the rest
    * from 28.
    *
    * @param length
    *   >= 1
    */
  def nonAmbiguousLower(length: Int): String = gen(LowercaseLettersAndNumbers.nonAmbiguous, length)

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
