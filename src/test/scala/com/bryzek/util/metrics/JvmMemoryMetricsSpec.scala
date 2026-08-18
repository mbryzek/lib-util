package com.bryzek.util.metrics

import ch.qos.logback.classic.Level
import com.bryzek.util.log.{KeyValueLoggerBuilder, LogCapture}
import com.bryzek.util.metrics.JvmMemoryMetrics.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.lang.management.ManagementFactory
import scala.jdk.CollectionConverters.*

class JvmMemoryMetricsSpec extends AnyWordSpec with Matchers {

  private val HeapUsedMb = 987L
  private val HeapMaxMb = 2000L

  private def pool(
    activeConnections: Int = 3,
    threadsAwaitingConnection: Int = 0,
    maximumPoolSize: Int = 10
  ): DbPool = DbPool(
    activeConnections = activeConnections,
    idleConnections = 7,
    totalConnections = 10,
    threadsAwaitingConnection = threadsAwaitingConnection,
    maximumPoolSize = maximumPoolSize
  )

  private def sample(
    heapUsedMb: Long = HeapUsedMb,
    heapMaxMb: Long = HeapMaxMb,
    dbPool: Option[DbPool] = Some(pool())
  ): Sample = Sample(
    heapUsedMb = heapUsedMb,
    heapMaxMb = heapMaxMb,
    nonHeapUsedMb = 150L,
    pools = PoolBreakdown(
      oldGenUsedMb = 300L,
      oldGenMaxMb = 1500L,
      edenUsedMb = 200L,
      survivorUsedMb = 25L,
      metaspaceUsedMb = 120L
    ),
    gcCount = 900L,
    gcTimeMs = 8000L,
    gcCountDelta = 7L,
    gcTimeMsDelta = 42L,
    dbPool = dbPool
  )

  /** Emits through the real builder every app logs through, so the assertions below read the string
    * NewRelic actually receives rather than a reconstruction of it.
    */
  private def emitted(s: Sample): LogCapture.Captured =
    LogCapture.captureOne("jvm-memory-metrics-spec") { logger => emit(KeyValueLoggerBuilder(logger), s) }

  "emit" must {

    // The platform-memory-improvement playbook parses these back out of the message with
    // aparse('%<field>: *,%'), which matches nothing — rather than erroring — if the field is absent
    // or unterminated. So the trailing COMMA is part of the contract: it is only there while some
    // other key sorts after this one, and the logger sorts its keys alphabetically.
    "write every field the memory playbook ranks on in the shape NRQL aparse needs" in {
      val line = emitted(sample()).message

      Map(
        "heapPercent" -> "49",
        "oldGenUsedMb" -> "300",
        "gcCountDelta" -> "7",
        "gcTimeMsDelta" -> "42",
        "heapUsedMb" -> HeapUsedMb.toString,
        "heapMaxMb" -> HeapMaxMb.toString
      ).foreach { case (field, value) =>
        line must include(s"$field: $value,")
      }
    }

    "keep the same fields parseable when the pool sample is absent and survivorUsedMb sorts last" in {
      val line = emitted(sample(dbPool = None)).message
      Seq("heapPercent: 49,", "oldGenUsedMb: 300,", "gcCountDelta: 7,", "gcTimeMsDelta: 42,").foreach { kv =>
        line must include(kv)
      }
      line must not include "activeConnections"
      line must endWith("survivorUsedMb: 25")
    }

    "keep the remaining fields, so nothing already querying them breaks" in {
      val line = emitted(sample()).message
      Seq(
        "oldGenMaxMb: 1500",
        "edenUsedMb: 200",
        "survivorUsedMb: 25",
        "metaspaceUsedMb: 120",
        "nonHeapUsedMb: 150",
        "gcCount: 900",
        "gcTimeMs: 8000"
      ).foreach { kv =>
        line must include(kv)
      }
    }

    // The four gauges NEW_RELIC_JMX_ENABLED=false removes from APM (Busy / Idle / Total / Threads
    // Awaiting Count), read against the maximumPoolSize they mean nothing without. Comma-terminated
    // for the same aparse reason as everything above — only totalConnections, which nothing parses,
    // is allowed to sort last.
    "carry the HikariCP pool gauges the JMX metrics used to" in {
      val line = emitted(sample()).message
      Seq(
        "activeConnections: 3,",
        "idleConnections: 7,",
        "maximumPoolSize: 10,",
        "threadsAwaitingConnection: 0,"
      ).foreach { kv =>
        line must include(kv)
      }
      line must endWith("totalConnections: 10")
    }

    "log heap stats at info below the warn threshold" in {
      sample().heapPercent mustBe 49L
      val captured = emitted(sample())
      captured.level mustBe Level.INFO
      captured.message must include("JvmMemoryMetrics: Heap stats")
    }

    "warn once heap reaches the warn threshold" in {
      val s = sample(heapUsedMb = HeapMaxMb * HeapWarnPercent / 100)
      s.heapPercent mustBe HeapWarnPercent
      val captured = emitted(s)
      captured.level mustBe Level.WARN
      captured.message must include("JvmMemoryMetrics: Heap pressure detected")
    }

    "warn on pool saturation while the heap is healthy" in {
      val captured = emitted(sample(dbPool = Some(pool(threadsAwaitingConnection = 1))))
      captured.level mustBe Level.WARN
      captured.message must include("JvmMemoryMetrics: Pool saturation detected")
    }

    "name both when the heap and the pool are in trouble at once" in {
      val s = sample(
        heapUsedMb = HeapMaxMb * HeapWarnPercent / 100,
        dbPool = Some(pool(threadsAwaitingConnection = 1))
      )
      emitted(s).message must include("JvmMemoryMetrics: Heap pressure and pool saturation detected")
    }
  }

  "heapPercent" must {
    "be zero when the JVM reports no heap maximum, rather than dividing by it" in {
      sample(heapMaxMb = 0L).heapPercent mustBe 0L
    }
  }

  "DbPool.isSaturated" must {

    "be false for a pool with idle headroom and nothing waiting" in {
      pool(activeConnections = 7).isSaturated mustBe false
    }

    "be true once the pool reaches 80% of its ceiling, before anything blocks" in {
      pool(activeConnections = 8).isSaturated mustBe true
    }

    "be true whenever a thread is waiting, however small the pool reads" in {
      pool(activeConnections = 0, threadsAwaitingConnection = 1).isSaturated mustBe true
    }

    "not call an idle pool saturated when Hikari reports no maximum" in {
      pool(activeConnections = 0, maximumPoolSize = 0).isSaturated mustBe false
    }
  }

  "summarize" must {

    "break the live JVM pools down into non-negative generations" in {
      val b = summarize(ManagementFactory.getMemoryPoolMXBeans.asScala.toList)

      b.oldGenUsedMb must be >= 0L
      b.oldGenMaxMb must be >= 0L
      b.edenUsedMb must be >= 0L
      b.survivorUsedMb must be >= 0L
      b.metaspaceUsedMb must be >= 0L

      // Every standard collector exposes Eden plus Old or Tenured, so at least one heap category
      // must be populated — a breakdown of all zeros means the matchers no longer recognize the
      // collector prod is running, which is the silent-zero failure this whole line exists to avoid.
      (b.oldGenUsedMb + b.edenUsedMb + b.survivorUsedMb) must be > 0L

      // Metaspace is always present on HotSpot.
      b.metaspaceUsedMb must be > 0L
    }

    "return zeros for an empty pool list" in {
      summarize(Nil) mustBe PoolBreakdown(0L, 0L, 0L, 0L, 0L)
    }
  }
}
