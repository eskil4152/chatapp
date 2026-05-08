package com.blikeng.chatapp.messagingTests

import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.Cursor
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.UUID

@ExtendWith(MockKExtension::class)
class PresenceHandlerTests {
    // ==========================
    // Tests for PresenceHandler. Verifies:
    // - User online counter-increment and decrement
    // - Redis key cleanup when user count reaches zero or null
    // - Online status lookup
    // ==========================

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK lateinit var valueOps: ValueOperations<String, String>

    @InjectMockKs lateinit var presenceHandler: PresenceHandler

    @Test
    fun shouldDeleteAllStalePresenceKeysOnStartup() {
        val cursor = mockk<Cursor<String>>()

        every { cursor.hasNext() } returnsMany listOf(true, true, false)
        every { cursor.next() } returnsMany listOf("presence:user:abc", "presence:user:def")
        every { redisTemplate.scan(any()) } returns cursor
        every { redisTemplate.delete(any<Collection<String>>()) } returns 2L

        presenceHandler.clearStalePresence()

        verify(exactly = 1) { redisTemplate.delete(any<Collection<String>>()) }
    }

    @Test
    fun shouldNotThrowWhenRedisScanFailsDuringStartup() {
        every { redisTemplate.scan(any()) } throws RuntimeException("Redis unavailable")

        assertDoesNotThrow { presenceHandler.clearStalePresence() }
    }

    @Test
    fun shouldIncrementUserPresenceOnConnect() {
        val userId = UUID.randomUUID()
        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.increment(PresenceKeys.userPresence(userId)) } returns 1L

        presenceHandler.userConnected(userId)

        verify(exactly = 1) { valueOps.increment(PresenceKeys.userPresence(userId)) }
    }

    @Test
    fun shouldDeletePresenceKeyWhenDisconnectCountIsNull() {
        val userId = UUID.randomUUID()
        val key = PresenceKeys.userPresence(userId)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.decrement(key) } returns null
        every { redisTemplate.delete(key) } returns true

        presenceHandler.userDisconnected(userId)

        verify(exactly = 1) { valueOps.decrement(key) }
        verify(exactly = 1) { redisTemplate.delete(key) }
    }

    @Test
    fun shouldDeletePresenceKeyWhenDisconnectCountIsZero() {
        val userId = UUID.randomUUID()
        val key = PresenceKeys.userPresence(userId)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.decrement(key) } returns 0L
        every { redisTemplate.delete(key) } returns true

        presenceHandler.userDisconnected(userId)

        verify(exactly = 1) { valueOps.decrement(key) }
        verify(exactly = 1) { redisTemplate.delete(key) }
    }

    @Test
    fun shouldDeletePresenceKeyWhenDisconnectCountIsNegative() {
        val userId = UUID.randomUUID()
        val key = PresenceKeys.userPresence(userId)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.decrement(key) } returns -1L
        every { redisTemplate.delete(key) } returns true

        presenceHandler.userDisconnected(userId)

        verify(exactly = 1) { valueOps.decrement(key) }
        verify(exactly = 1) { redisTemplate.delete(key) }
    }

    @Test
    fun shouldKeepPresenceKeyWhenDisconnectCountRemainsPositive() {
        val userId = UUID.randomUUID()
        val key = PresenceKeys.userPresence(userId)

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.decrement(key) } returns 2L

        presenceHandler.userDisconnected(userId)

        verify(exactly = 1) { valueOps.decrement(key) }
        verify(exactly = 0) { redisTemplate.delete(any<String>()) }
    }

    @Test
    fun shouldReturnTrueWhenUserOnlineCountIsPositive() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get(PresenceKeys.userPresence(userId)) } returns "2"

        assertTrue(presenceHandler.isUserOnline(userId))
    }

    @Test
    fun shouldReturnFalseWhenUserOnlineCountIsZero() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get(PresenceKeys.userPresence(userId)) } returns "0"

        assertFalse(presenceHandler.isUserOnline(userId))
    }

    @Test
    fun shouldReturnFalseWhenUserOnlineCountIsInvalid() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get(PresenceKeys.userPresence(userId)) } returns "not-a-number"

        assertFalse(presenceHandler.isUserOnline(userId))
    }

    @Test
    fun shouldReturnFalseWhenUserOnlineCountIsMissing() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.get(PresenceKeys.userPresence(userId)) } returns null

        assertFalse(presenceHandler.isUserOnline(userId))
    }

    // ==========================
    // getOnlineUsers
    // ==========================
    @Test
    fun shouldReturnEmptySetForEmptyInput() {
        val result = presenceHandler.getOnlineUsers(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun shouldReturnOnlineUsersFromBatch() {
        val online = UUID.randomUUID()
        val offline = UUID.randomUUID()
        val keys = listOf(PresenceKeys.userPresence(online), PresenceKeys.userPresence(offline))

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.multiGet(keys) } returns listOf("1", "0")

        val result = presenceHandler.getOnlineUsers(listOf(online, offline))

        assertEquals(setOf(online), result)
    }

    @Test
    fun shouldExcludeUsersWithNullValueFromBatch() {
        val online = UUID.randomUUID()
        val missing = UUID.randomUUID()
        val keys = listOf(PresenceKeys.userPresence(online), PresenceKeys.userPresence(missing))

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.multiGet(keys) } returns listOf("2", null)

        val result = presenceHandler.getOnlineUsers(listOf(online, missing))

        assertEquals(setOf(online), result)
    }

    @Test
    fun shouldReturnEmptySetWhenMultiGetReturnsNull() {
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForValue() } returns valueOps
        every { valueOps.multiGet(any()) } returns null

        val result = presenceHandler.getOnlineUsers(listOf(userId))

        assertTrue(result.isEmpty())
    }
}
