package com.blikeng.chatapp.messaging.redis

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ScanOptions
import org.springframework.stereotype.Component
import java.util.*

// ==========================
// Tracks global user presence in Redis via a session-count counter.
// A user is online as long as their counter is above zero.
// ==========================
@Component
class PresenceHandler(
    private val redisTemplate: RedisTemplate<String, String>
) {
    @EventListener(ApplicationReadyEvent::class)
    fun clearStalePresence() {
        try {
            val scanOptions = ScanOptions.scanOptions()
                .match("presence:user:*")
                .build()

            val cursor = redisTemplate.scan(scanOptions)
            val keys = cursor.asSequence().toList()

            if (keys.isNotEmpty()) {
                redisTemplate.delete(keys)
            }

        } catch (e: Exception) {
            println("Redis cleanup failed: ${e.message}")
        }
    }

    fun userConnected(userId: UUID): Long {
        return redisTemplate.opsForValue().increment(PresenceKeys.userPresence(userId))
    }

    fun userDisconnected(userId: UUID): Long {
        val key = PresenceKeys.userPresence(userId)
        val value = redisTemplate.opsForValue().decrement(key)

        if (value == null || value <= 0) {
            redisTemplate.delete(key)
        }

        return value ?: 0L
    }

    fun isUserOnline(userId: UUID): Boolean {
        val value = redisTemplate.opsForValue()[PresenceKeys.userPresence(userId)]
        return value?.toLongOrNull()?.let { it > 0 } == true
    }
}
