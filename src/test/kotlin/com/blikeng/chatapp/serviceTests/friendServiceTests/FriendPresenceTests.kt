package com.blikeng.chatapp.serviceTests.friendServiceTests

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations

@ExtendWith(MockKExtension::class)
class FriendPresenceTests {
    // ==========================
    // Tests for FriendsService.notifyFriends.
    // Verifies:
    // - Online and offline payloads published to each friend's Redis channel
    // - Correct friend ID resolved when user is userA or userB
    // - No notifications sent when user has no friends
    // ==========================

    @InjectMockKs private lateinit var friendService: FriendService

    @MockK private lateinit var friendsRepository: FriendsRepository

    @MockK private lateinit var userService: UserService

    @MockK private lateinit var userRepository: UserRepository

    @MockK private lateinit var presenceHandler: PresenceHandler

    @RelaxedMockK private lateinit var redisTemplate: RedisTemplate<String, String>
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setupCache() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
    }

    @Test
    fun shouldNotifyFriendsWhenUserComesOnline() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)

        val payloadSlot = slot<String>()
        every { redisTemplate.convertAndSend("user:${friend.id}", capture(payloadSlot)) } returns 1L

        friendService.notifyFriends(user.id, true)

        verify(exactly = 1) { redisTemplate.convertAndSend("user:${friend.id}", any()) }
        assertTrue(payloadSlot.captured.contains(user.id.toString()))
        assertTrue(payloadSlot.captured.contains("true"))
    }

    @Test
    fun shouldNotifyFriendsWhenUserGoesOffline() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)

        val payloadSlot = slot<String>()
        every { redisTemplate.convertAndSend("user:${friend.id}", capture(payloadSlot)) } returns 1L

        friendService.notifyFriends(user.id, false)

        verify(exactly = 1) { redisTemplate.convertAndSend("user:${friend.id}", any()) }
        assertTrue(payloadSlot.captured.contains(user.id.toString()))
        assertTrue(payloadSlot.captured.contains("false"))
    }

    @Test
    fun shouldResolveFriendIdCorrectlyWhenUserIsUserB() {
        val friend = UserEntity(username = "friend", password = "")
        val user = UserEntity(username = "user", password = "")
        val friendship = FriendsEntity(id = FriendsId(friend.id, user.id), userA = friend, userB = user)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { redisTemplate.convertAndSend("user:${friend.id}", any()) } returns 1L

        friendService.notifyFriends(user.id, true)

        verify(exactly = 1) { redisTemplate.convertAndSend("user:${friend.id}", any()) }
        verify(exactly = 0) { redisTemplate.convertAndSend("user:${user.id}", any()) }
    }

    @Test
    fun shouldSendNoNotificationsWhenUserHasNoFriends() {
        val user = UserEntity(username = "user", password = "")

        every { friendsRepository.findFriendsForUser(user.id) } returns emptyList()

        friendService.notifyFriends(user.id, true)

        verify(exactly = 0) { redisTemplate.convertAndSend(any(), any<String>()) }
    }

    @Test
    fun shouldPublishToEachFriendChannel() {
        val user = UserEntity(username = "user", password = "")
        val friend1 = UserEntity(username = "friend1", password = "")
        val friend2 = UserEntity(username = "friend2", password = "")
        val friendship1 = FriendsEntity(id = FriendsId(user.id, friend1.id), userA = user, userB = friend1)
        val friendship2 = FriendsEntity(id = FriendsId(user.id, friend2.id), userA = user, userB = friend2)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship1, friendship2)
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L

        friendService.notifyFriends(user.id, true)

        verify(exactly = 1) { redisTemplate.convertAndSend("user:${friend1.id}", any()) }
        verify(exactly = 1) { redisTemplate.convertAndSend("user:${friend2.id}", any()) }
    }

    // ==========================
    // getOnlineFriends (on-connect snapshot)
    // ==========================
    @Test
    fun shouldReturnSnapshotWithOnlineFriend() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { presenceHandler.getOnlineUsers(listOf(friend.id)) } returns setOf(friend.id)
        every { userService.getAllById(listOf(friend.id)) } returns listOf(friend)

        val snapshot = friendService.getOnlineFriends(user.id)

        assertEquals(1, snapshot.friends.size)
        assertEquals(friend.id, snapshot.friends[0].userId)
        assertTrue(snapshot.friends[0].online)
    }

    @Test
    fun shouldReturnSnapshotWithOfflineFriend() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { presenceHandler.getOnlineUsers(listOf(friend.id)) } returns emptySet()
        every { userService.getAllById(listOf(friend.id)) } returns listOf(friend)

        val snapshot = friendService.getOnlineFriends(user.id)

        assertEquals(1, snapshot.friends.size)
        assertFalse(snapshot.friends[0].online)
    }

    @Test
    fun shouldReturnEmptySnapshotWhenUserHasNoFriends() {
        val user = UserEntity(username = "user", password = "")

        every { friendsRepository.findFriendsForUser(user.id) } returns emptyList()

        val snapshot = friendService.getOnlineFriends(user.id)

        assertTrue(snapshot.friends.isEmpty())
    }

    @Test
    fun shouldResolveCorrectFriendIdWhenUserIsUserB() {
        val friend = UserEntity(username = "friend", password = "")
        val user = UserEntity(username = "user", password = "")
        val friendship = FriendsEntity(id = FriendsId(friend.id, user.id), userA = friend, userB = user)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { presenceHandler.getOnlineUsers(listOf(friend.id)) } returns setOf(friend.id)
        every { userService.getAllById(listOf(friend.id)) } returns listOf(friend)

        val snapshot = friendService.getOnlineFriends(user.id)

        assertEquals(friend.id, snapshot.friends[0].userId)
    }
}
