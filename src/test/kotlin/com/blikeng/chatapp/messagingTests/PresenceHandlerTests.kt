package com.blikeng.chatapp.messagingTests

import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SetOperations
import org.springframework.data.redis.core.ValueOperations
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class PresenceHandlerTests {
    // ==========================
    // Tests for PresenceHandler. Verifies:
    // - User online counter-increment and decrement
    // - Redis key cleanup when user count reaches zero or null
    // - Online status lookup
    // - Room membership add and remove
    // - Room member parsing, invalid UUID filtering, and empty fallback
    // ==========================

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var valueOps: ValueOperations<String, String>
    @MockK lateinit var setOps: SetOperations<String, String>

    @InjectMockKs lateinit var presenceHandler: PresenceHandler

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

    @Test
    fun shouldAddUserToRoomPresence() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForSet() } returns setOps
        every { setOps.add(PresenceKeys.roomPresence(roomId), userId.toString()) } returns 1L

        presenceHandler.userJoinedRoom(roomId, userId)

        verify(exactly = 1) {
            setOps.add(PresenceKeys.roomPresence(roomId), userId.toString())
        }
    }

    @Test
    fun shouldRemoveUserFromRoomPresence() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { redisTemplate.opsForSet() } returns setOps
        every { setOps.remove(PresenceKeys.roomPresence(roomId), userId.toString()) } returns 1L

        presenceHandler.userLeftRoom(roomId, userId)

        verify(exactly = 1) {
            setOps.remove(PresenceKeys.roomPresence(roomId), userId.toString())
        }
    }

    @Test
    fun shouldReturnUsersInRoomAndIgnoreInvalidUUIDs() {
        val roomId = UUID.randomUUID()
        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()

        every { redisTemplate.opsForSet() } returns setOps
        every { setOps.members(PresenceKeys.roomPresence(roomId)) } returns setOf(
            user1.toString(),
            "not-a-uuid",
            user2.toString()
        )

        val result = presenceHandler.getUsersInRoom(roomId)

        assertEquals(setOf(user1, user2), result)
    }

    @Test
    fun shouldReturnEmptySetWhenRoomPresenceMissing() {
        val roomId = UUID.randomUUID()

        every { redisTemplate.opsForSet() } returns setOps
        every { setOps.members(PresenceKeys.roomPresence(roomId)) } returns null

        val result = presenceHandler.getUsersInRoom(roomId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun shouldReturnEmptySetWhenRoomPresenceContainsOnlyInvalidUuids() {
        val roomId = UUID.randomUUID()

        every { redisTemplate.opsForSet() } returns setOps
        every { setOps.members(PresenceKeys.roomPresence(roomId)) } returns setOf("bad", "also-bad")

        val result = presenceHandler.getUsersInRoom(roomId)

        assertTrue(result.isEmpty())
    }
}