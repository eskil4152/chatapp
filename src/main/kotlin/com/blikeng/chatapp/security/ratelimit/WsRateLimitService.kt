package com.blikeng.chatapp.security.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineStatsCounter
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*

// ==========================
// File for WebSocket message rate limiting. Injected in WebSocketHandler.
// Current settings set 10 initial chats and a max of 10. Regenerates 10 per minute, evenly spread.
// ==========================
@Service
class WsRateLimitService(
    meterRegistry: MeterRegistry
) {
    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .recordStats { CaffeineStatsCounter(meterRegistry, "ws.rate.limit.buckets") }
        .build<UUID, Bucket>()

    fun tryConsumeMessage(userId: UUID): Boolean {
        val bucket = buckets.get(userId) {
            Bucket.builder()
                .addLimit(
                    Bandwidth.builder()
                        .capacity(60)
                        .refillGreedy(30, Duration.ofMinutes(1))
                        .build()
                )
                .build()
        }

        return bucket.tryConsume(1)
    }
}