package com.bryzek.util

import org.apache.commons.codec.binary.Base64

import java.nio.charset.Charset

object Base64Util {
  private val Utf8: Charset = java.nio.charset.Charset.forName("UTF-8")

  def encode(value: String): String = {
    new String(Base64.encodeBase64(value.getBytes(Utf8)), Utf8)
  }

  def decode(value: String): String = {
    new String(Base64.decodeBase64(value.getBytes(Utf8)), Utf8)
  }

}
