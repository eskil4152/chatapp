package com.blikeng.chatapp.security.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

// ==========================
// File for WebSocket message rate limiting. Injected in WebSocketHandler.
// Current settings set 10 initial chats and a max of 10. Regenerates 10 per minute, evenly spread.
// ==========================
@Service
class WsRateLimitService(
    meterRegistry: MeterRegistry
) {
    private val buckets = ConcurrentHashMap<UUID, Bucket>()

    init {
        meterRegistry.gauge("ws.rate_limit.buckets", buckets) { it.size.toDouble() }
    }

    fun tryConsumeMessage(userId: UUID): Boolean {
        val bucket = buckets.computeIfAbsent(userId) {
            Bucket.builder()
                .addLimit(
                    Bandwidth.builder()
                        .capacity(10)
                        .refillGreedy(10, Duration.ofMinutes(1))
                        .build()
                )
                .build()
        }

        return bucket.tryConsume(1)
    }
}