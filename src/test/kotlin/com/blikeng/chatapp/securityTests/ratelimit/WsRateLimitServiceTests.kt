package com.blikeng.chatapp.securityTests.ratelimit

import com.blikeng.chatapp.security.ratelimit.WsRateLimitService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

// ==========================
// Tests for WsRateLimitService. Verifies:
// - Per-user WebSocket message rate limiting
// - Blocking of messages after limit exhaustion
// - Isolation of buckets between different users
// - Gauge registration and correct bucket count metrics
// ==========================
class WsRateLimitServiceTests {
    private val simpleMeterRegistry = SimpleMeterRegistry()

    @Test
    fun shouldAllowMessagesWithinLimit() {
        val service = WsRateLimitService(simpleMeterRegistry)

        val userId = UUID.randomUUID()

        repeat(10) {
            assertTrue(service.tryConsumeMessage(userId))
        }
    }

    @Test
    fun shouldBlockMessagesAboveLimit() {
        val service = WsRateLimitService(simpleMeterRegistry)

        val userId = UUID.randomUUID()

        repeat(10) {
            assertTrue(service.tryConsumeMessage(userId))
        }

        assertFalse(service.tryConsumeMessage(userId))
    }

    @Test
    fun shouldTrackWsRateLimitBucketsGauge() {
        val registry = SimpleMeterRegistry()
        val service = WsRateLimitService(registry)

        val gauge = registry.get("ws.rate_limit.buckets").gauge()

        assertEquals(0.0, gauge.value())

        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()

        service.tryConsumeMessage(user1)
        assertEquals(1.0, gauge.value())

        service.tryConsumeMessage(user2)
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun shouldKeepBucketsSeparatePerUser() {
        val service = WsRateLimitService(simpleMeterRegistry)

        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()

        repeat(10) {
            assertTrue(service.tryConsumeMessage(user1))
        }

        assertFalse(service.tryConsumeMessage(user1))
        assertTrue(service.tryConsumeMessage(user2))
    }
}