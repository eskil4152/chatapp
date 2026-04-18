package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.services.UserRevocationService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class UserRevocationServiceTests {
    // ==========================
    // Tests for UserRevocationService. Verifies:
    // - revoke: writes the revoked key to Redis with 24h TTL
    // - isRevoked: returns true when key exists, false when it does not
    // ==========================

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var valueOps: ValueOperations<String, String>

    @InjectMockKs lateinit var userRevocationService: UserRevocationService

    @Test
    fun shouldRevokeUser() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.set("revoked_user:$userId", "1", Duration.ofHours(24)) } returns Unit

        userRevocationService.revoke(userId)

        verify(exactly = 1) { valueOps.set("revoked_user:$userId", "1", Duration.ofHours(24)) }
    }

    @Test
    fun shouldReturnTrueWhenUserIsRevoked() {
        val userId = UUID.randomUUID()

        every { redisTemplate.hasKey("revoked_user:$userId") } returns true

        assertTrue(userRevocationService.isRevoked(userId))
    }

    @Test
    fun shouldReturnFalseWhenUserIsNotRevoked() {
        val userId = UUID.randomUUID()

        every { redisTemplate.hasKey("revoked_user:$userId") } returns false

        assertFalse(userRevocationService.isRevoked(userId))
    }

    @Test
    fun shouldReturnFalseWhenRedisReturnsNull() {
        val userId = UUID.randomUUID()

        every { redisTemplate.hasKey("revoked_user:$userId") } returns null

        assertFalse(userRevocationService.isRevoked(userId))
    }
}
