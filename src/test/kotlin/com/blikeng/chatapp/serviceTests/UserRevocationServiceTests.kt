package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.services.UserRevocationService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.UUID

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

    @Test
    fun shouldBanUser() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.set("banned_user:$userId", "1", Duration.ofHours(24)) } returns Unit

        userRevocationService.revokeBanned(userId)

        verify(exactly = 1) { valueOps.set("banned_user:$userId", "1", Duration.ofHours(24)) }
    }

    @Test
    fun shouldReturnTrueWhenUserIsBanned() {
        val userId = UUID.randomUUID()

        every { redisTemplate.hasKey("banned_user:$userId") } returns true

        assertTrue(userRevocationService.isBanned(userId))
    }

    @Test
    fun shouldReturnFalseWhenUserIsNotInBannedList() {
        val userId = UUID.randomUUID()

        every { redisTemplate.hasKey("banned_user:$userId") } returns false

        assertFalse(userRevocationService.isBanned(userId))
    }

    @Test
    fun shouldReturnFalseWhenRedisReturnsNullForBannedUser() {
        val userId = UUID.randomUUID()

        every { redisTemplate.hasKey("banned_user:$userId") } returns null

        assertFalse(userRevocationService.isBanned(userId))
    }

    @Test
    fun shouldDeleteBannedUserKeyOnUnRevoke() {
        val userId = UUID.randomUUID()

        every { redisTemplate.delete("banned_user:$userId") } returns true

        userRevocationService.unRevokeBanned(userId)

        verify(exactly = 1) { redisTemplate.delete("banned_user:$userId") }
    }
}
