package com.bryzek.util

import com.bryzek.util.BasicAuth.Credential
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BasicAuthSpec extends AnyWordSpec with Matchers {

  private def basic(userAndPassword: String): String = s"Basic ${Base64Util.encode(userAndPassword)}"

  "parse" must {
    "Absent without a header" in {
      BasicAuth.parse(None) mustBe Credential.Absent
    }

    "Absent for another scheme" in {
      BasicAuth.parse("Bearer abc") mustBe Credential.Absent
      BasicAuth.parse(Some("Negotiate abc")) mustBe Credential.Absent
    }

    "Present with the username as the token" in {
      BasicAuth.parse(basic("tok:")) mustBe Credential.Present("tok")
      BasicAuth.parse(basic("tok:secret")) mustBe Credential.Present("tok")
      BasicAuth.parse(basic("tok")) mustBe Credential.Present("tok")
    }

    "match the scheme case-insensitively" in {
      BasicAuth.parse(s"BASIC ${Base64Util.encode("tok:")}") mustBe Credential.Present("tok")
      BasicAuth.parse(s"basic ${Base64Util.encode("tok:")}") mustBe Credential.Present("tok")
    }

    "tolerate whitespace around the header and the encoded value" in {
      BasicAuth.parse(s"  Basic   ${Base64Util.encode("tok:")}  ") mustBe Credential.Present("tok")
    }

    "trim whitespace around the decoded token" in {
      BasicAuth.parse(basic(" tok :")) mustBe Credential.Present("tok")
      BasicAuth.parse(basic("\ttok\n:x")) mustBe Credential.Present("tok")
    }

    "Malformed for a Basic scheme with no credential" in {
      BasicAuth.parse("Basic") mustBe Credential.Malformed
      BasicAuth.parse("Basic ") mustBe Credential.Malformed
    }

    "Malformed for an empty or whitespace-only token" in {
      BasicAuth.parse(basic(":secret")) mustBe Credential.Malformed
      BasicAuth.parse(basic("   :secret")) mustBe Credential.Malformed
    }
  }
}
