package com.blikeng.chatapp.services

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

// ==========================
// Tracks deleted user IDs in Redis so the auth filter can reject their
// in-flight JWTs without hitting the database. TTL matches JWT lifetime (24h).
// ==========================
@Service
class UserRevocationService(
    private val redisTemplate: RedisTemplate<String, String>,
) {
    fun revoke(userId: UUID) {
        redisTemplate.opsForValue().set("revoked_user:$userId", "1", Duration.ofHours(24))
    }

    fun isRevoked(userId: UUID): Boolean = redisTemplate.hasKey("revoked_user:$userId") == true

    fun revokeBanned(userId: UUID) {
        redisTemplate.opsForValue().set("banned_user:$userId", "1", Duration.ofHours(24))
    }

    fun isBanned(userId: UUID): Boolean = redisTemplate.hasKey("banned_user:$userId") == true

    fun unRevokeBanned(userId: UUID) {
        redisTemplate.delete("banned_user:$userId")
    }
}
