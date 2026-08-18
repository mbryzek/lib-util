package com.bryzek.util.log

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SlowWorkLineSpec extends AnyWordSpec with Matchers {

  "lineIfSlow" must {

    "say nothing below the threshold" in {
      SlowWorkLine.lineIfSlow("request", durationMs = 1999L, thresholdMs = 2000L) mustBe None
    }

    "emit at the threshold, so the boundary is measured rather than skipped" in {
      SlowWorkLine.lineIfSlow("request", durationMs = 2000L, thresholdMs = 2000L) mustBe Some(
        "request duration_ms=2000"
      )
    }

    // The queries that read this line are written once and pointed at every app, so the tokens are
    // the contract: `<label> duration_ms=<n>` then each field as `k=v`, space separated.
    "render the fields as k=v in the order given" in {
      SlowWorkLine.lineIfSlow(
        "request",
        durationMs = 2500L,
        thresholdMs = 2000L,
        "method" -> "GET",
        "path" -> "/_internal_/healthcheck",
        "status" -> "200"
      ) mustBe Some("request duration_ms=2500 method=GET path=/_internal_/healthcheck status=200")
    }

    "keep one threshold for HTTP across every app, so the distributions are comparable" in {
      SlowWorkLine.HttpRequestMillis mustBe 2000L
    }
  }
}
