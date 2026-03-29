package com.blikeng.chatapp.messaging.redis

import jakarta.annotation.PostConstruct
import org.springframework.data.redis.core.RedisTemplate
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
    @PostConstruct
    fun clearStalePresence() {
        val keys = redisTemplate.keys("presence:user:*")
        if (keys.isNotEmpty()) redisTemplate.delete(keys)
    }

    fun userConnected(userId: UUID) {
        redisTemplate.opsForValue().increment(PresenceKeys.userPresence(userId))
    }

    fun userDisconnected(userId: UUID) {
        val key = PresenceKeys.userPresence(userId)
        val value = redisTemplate.opsForValue().decrement(key)

        if (value == null || value <= 0) {
            redisTemplate.delete(key)
        }
    }

    fun isUserOnline(userId: UUID): Boolean {
        val value = redisTemplate.opsForValue()[PresenceKeys.userPresence(userId)]
        return value?.toLongOrNull()?.let { it > 0 } == true
    }
}
