package com.blikeng.chatapp.security.ratelimit

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.cache.CaffeineStatsCounter
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RateLimitService(
    meterRegistry: MeterRegistry
) {
    private val buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(10))
        .recordStats { CaffeineStatsCounter(meterRegistry, "app.ratelimit.http.buckets") }
        .build<String, Bucket>()
        .also { cache -> Gauge.builder("app.ratelimit.http.buckets", cache) { it.asMap().size.toDouble() }.register(meterRegistry) }

    fun tryConsume(key: String, maxTokens: Long, window: Duration): Boolean {
        val bucket = buckets.get(key) {
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