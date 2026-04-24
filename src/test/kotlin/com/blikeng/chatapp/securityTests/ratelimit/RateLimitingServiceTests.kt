package com.blikeng.chatapp.securityTests.ratelimit

import com.blikeng.chatapp.security.ratelimit.RateLimitService
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration

// ==========================
// Tests for RateLimitService. Verifies:
// - Token bucket rate limiting for HTTP endpoints
// - Blocking of requests after token exhaustion
// - Separation of buckets by unique rate-limit keys
// - Gauge registration and correct bucket count metrics
// ==========================
class RateLimitServiceTests {
    private val simpleMeterRegistry = SimpleMeterRegistry()

    @Test
    fun shouldTrackHttpRateLimitBucketsGauge() {
        val registry = SimpleMeterRegistry()
        val service = RateLimitService(registry)

        val gauge = registry.get("app.ratelimit.http.buckets").gauge()

        assertEquals(0.0, gauge.value())

        service.tryConsume("login:1.1.1.1", 5, Duration.ofMinutes(1))
        assertEquals(1.0, gauge.value())

        service.tryConsume("register:1.1.1.1", 10, Duration.ofMinutes(1))
        assertEquals(2.0, gauge.value())
    }

    @Test
    fun shouldAllowRequestsWithinLimit() {
        val service = RateLimitService(simpleMeterRegistry)

        repeat(5) {
            assertTrue(
                service.tryConsume(
                    key = "login:127.0.0.1",
                    maxTokens = 5,
                    window = Duration.ofMinutes(1)
                )
            )
        }
    }

    @Test
    fun shouldBlockRequestsAboveLimit() {
        val service = RateLimitService(simpleMeterRegistry)

        repeat(5) {
            assertTrue(
                service.tryConsume(
                    key = "login:127.0.0.1",
                    maxTokens = 5,
                    window = Duration.ofMinutes(1)
                )
            )
        }

        assertFalse(
            service.tryConsume(
                key = "login:127.0.0.1",
                maxTokens = 5,
                window = Duration.ofMinutes(1)
            )
        )
    }

    @Test
    fun shouldKeepBucketsSeparateByKey() {
        val service = RateLimitService(simpleMeterRegistry)

        repeat(5) {
            assertTrue(service.tryConsume("login:127.0.0.1", 5, Duration.ofMinutes(1)))
        }

        assertFalse(service.tryConsume("login:127.0.0.1", 5, Duration.ofMinutes(1)))

        assertTrue(service.tryConsume("register:127.0.0.1", 10, Duration.ofMinutes(1)))
    }
}