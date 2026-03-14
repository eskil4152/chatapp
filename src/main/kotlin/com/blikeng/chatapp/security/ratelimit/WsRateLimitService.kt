package com.blikeng.chatapp.security.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Service
class WsRateLimitService {
    private val buckets = ConcurrentHashMap<UUID, Bucket>()

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