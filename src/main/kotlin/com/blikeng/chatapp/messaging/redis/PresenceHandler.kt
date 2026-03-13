package com.blikeng.chatapp.messaging.redis

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.*

@Component
class PresenceHandler(
    private val redisTemplate: RedisTemplate<String, String>
) {
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
        val value = redisTemplate.opsForValue().get(PresenceKeys.userPresence(userId))
        return value?.toLongOrNull()?.let { it > 0 } == true
    }

    fun userJoinedRoom(roomId: UUID, userId: UUID) {
        redisTemplate.opsForSet().add(PresenceKeys.roomPresence(roomId), userId.toString())
    }

    fun userLeftRoom(roomId: UUID, userId: UUID) {
        redisTemplate.opsForSet().remove(PresenceKeys.roomPresence(roomId), userId.toString())
    }

    fun getUsersInRoom(roomId: UUID): Set<UUID> {
        return redisTemplate.opsForSet()
            .members(PresenceKeys.roomPresence(roomId))
            ?.mapNotNull {
                try {
                    UUID.fromString(it)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            ?.toSet()
            ?: emptySet()
    }
}