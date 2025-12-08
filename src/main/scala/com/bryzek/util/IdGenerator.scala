package com.bryzek.util

import java.util.UUID

case class IdGenerator(prefix: String) {
  def randomId(): String = {
    prefix + "-" + UUID.randomUUID().toString.replaceAll("\\-", "")
  }
}
