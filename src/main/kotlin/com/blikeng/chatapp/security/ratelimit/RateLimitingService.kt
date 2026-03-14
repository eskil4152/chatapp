package com.blikeng.chatapp.security.ratelimit

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class RateLimitService(
    meterRegistry: MeterRegistry
) {
    private val buckets = ConcurrentHashMap<String, Bucket>()

    init {
        meterRegistry.gauge("rate_limit.buckets", buckets) { it.size.toDouble() }
    }

    fun tryConsume(key: String, maxTokens: Long, window: Duration): Boolean {
        val bucket = buckets.computeIfAbsent(key) {
            Bucket.builder()
                .addLimit(
                    Bandwidth.builder()
                        .capacity(maxTokens)
                        .refillGreedy(maxTokens, window)
                        .build()
                )
                .build()
        }

        return bucket.tryConsume(1)
    }
}