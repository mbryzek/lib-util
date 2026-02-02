package com.bryzek.util

import org.apache.commons.codec.binary.Base64

import java.nio.charset.Charset

object Base64Util {
  private val Utf8: Charset = java.nio.charset.Charset.forName("UTF-8")

  def encode(value: String): String = {
    encodeBytes(value.getBytes(Utf8))
  }

  def encodeBytes(bytes: Array[Byte]): String = {
    new String(Base64.encodeBase64(bytes), Utf8)
  }

  def decode(value: String): String = {
    new String(decodeBytes(value.getBytes(Utf8)), Utf8)
  }

  def decodeBytes(bytes: String): Array[Byte] = {
    decodeBytes(bytes.getBytes(Utf8))
  }

  def decodeBytes(bytes: Array[Byte]): Array[Byte] = {
    Base64.decodeBase64(bytes)
  }

}
