package com.blikeng.chatapp.securityTests.ratelimit

import com.blikeng.chatapp.security.ratelimit.RateLimitingService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

// ==========================
// Tests for RateLimitService. Verifies:
// - Token bucket rate limiting for HTTP endpoints
// - Blocking of requests after token exhaustion
// - Separation of buckets by unique rate-limit keys
// - Gauge registration and correct bucket count metrics
// ==========================
class RateLimitingServiceTests {
    private val simpleMeterRegistry = SimpleMeterRegistry()

    @Test
    fun shouldTrackHttpRateLimitBucketsGauge() {
        val registry = SimpleMeterRegistry()
        val service = RateLimitingService(registry)

        val gauge = registry.get("app.ratelimit.http.buckets").gauge()

        assertEquals(0.0, gauge.value())

        service.tryConsume("login:1.1.1.1", 5, Duration.ofMinutes(1))
        assertEquals(1.0, gauge.value())

        service.tryConsume("register:1.1.1.1", 10, Duration.ofMinutes(1))
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun shouldAllowRequestsWithinLimit() {
        val service = RateLimitingService(simpleMeterRegistry)

        repeat(5) {
            assertTrue(
                service.tryConsume(
                    key = "login:127.0.0.1",
                    maxTokens = 5,
                    window = Duration.ofMinutes(1),
                ),
            )
        }
    }

    @Test
    fun shouldBlockRequestsAboveLimit() {
        val service = RateLimitingService(simpleMeterRegistry)

        repeat(5) {
            assertTrue(
                service.tryConsume(
                    key = "login:127.0.0.1",
                    maxTokens = 5,
                    window = Duration.ofMinutes(1),
                ),
            )
        }

        assertFalse(
            service.tryConsume(
                key = "login:127.0.0.1",
                maxTokens = 5,
                window = Duration.ofMinutes(1),
            ),
        )
    }

    @Test
    fun shouldKeepBucketsSeparateByKey() {
        val service = RateLimitingService(simpleMeterRegistry)

        repeat(5) {
            assertTrue(service.tryConsume("login:127.0.0.1", 5, Duration.ofMinutes(1)))
        }

        assertFalse(service.tryConsume("login:127.0.0.1", 5, Duration.ofMinutes(1)))

        assertTrue(service.tryConsume("register:127.0.0.1", 10, Duration.ofMinutes(1)))
    }
}
