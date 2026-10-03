package com.bryzek.util

/** Parses an HTTP `Authorization` header carrying a Basic credential whose username is the token.
  *
  * Pure over the header string so every Play app reads a Basic token the same way; the apps differ
  * only in what a token authenticates.
  */
object BasicAuth {

  private val Scheme = "basic"

  /** Three states, because "the caller presented no Basic credential" and "the caller presented a
    * Basic credential we could not parse" must not collapse to the same answer. A caller that read
    * both as "no token" would let a truncated or mistyped token fall through to whatever credential
    * the request also carries (a session header), so an automation whose token broke in transit
    * would keep working as whoever was signed in on that machine.
    */
  enum Credential {
    case Absent
    case Malformed
    case Present(token: String)
  }

  def parse(header: Option[String]): Credential = {
    header match {
      case None => Credential.Absent
      case Some(h) => parse(h)
    }
  }

  def parse(header: String): Credential = {
    // Split scheme from credential rather than matching a "basic " prefix: a bare
    // `Authorization: Basic` arrives trimmed, so a prefix test misses it and reads it as no
    // credential at all. It is a Basic credential the client failed to supply -- Malformed.
    val (scheme, encoded) = header.trim.span(_ != ' ')
    if (!scheme.equalsIgnoreCase(Scheme)) {
      // Another scheme (Bearer, Negotiate) is not ours to reject -- it is not a Basic credential at
      // all, so another header is still the caller's intended credential.
      Credential.Absent
    } else {
      // The decoded token is trimmed: whitespace around a token is never part of it, and every
      // app must agree on that so a token cannot authenticate in one product and fail in another.
      scala.util
        .Try(Base64Util.decode(encoded.trim))
        .toOption
        .flatMap(_.split(":", 2).headOption)
        .map(_.trim)
        .filter(_.nonEmpty) match {
        case Some(token) => Credential.Present(token)
        case None => Credential.Malformed
      }
    }
  }

}
