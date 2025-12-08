package com.bryzek.util

case class SecureIdGenerator(prefix: String) {

  private val idGenerator = IdGenerator(prefix)
  private val random = Random()
  def randomId(): String = {
    idGenerator.randomId() ++ random.alphaNumeric(8)
  }

}
