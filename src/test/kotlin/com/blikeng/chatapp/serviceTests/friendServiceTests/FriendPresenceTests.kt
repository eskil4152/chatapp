package com.blikeng.chatapp.serviceTests.friendServiceTests

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.UserService
import com.blikeng.chatapp.websocket.SessionRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@ExtendWith(MockKExtension::class)
class FriendPresenceTests {
    // ==========================
    // Tests for FriendsService.notifyFriends.
    // Verifies:
    // - Online and offline payloads sent to friend's open sessions
    // - Correct friend ID resolved when user is userA or userB
    // - Friends with no sessions or closed sessions are skipped
    // ==========================

    @InjectMockKs private lateinit var friendService: FriendService
    @MockK private lateinit var friendsRepository: FriendsRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var presenceHandler: PresenceHandler
    @MockK private lateinit var sessionRegistry: SessionRegistry
    private val objectMapper = ObjectMapper()

    @Test
    fun shouldNotifyFriendsWhenUserComesOnline() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs
        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(friend.id to CopyOnWriteArraySet(listOf(session)))

        friendService.notifyFriends(user.id, true)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains(user.id.toString()))
        assertTrue(msgSlot.captured.payload.contains("true"))
    }

    @Test
    fun shouldNotifyFriendsWhenUserGoesOffline() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs
        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(friend.id to CopyOnWriteArraySet(listOf(session)))

        friendService.notifyFriends(user.id, false)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains(user.id.toString()))
        assertTrue(msgSlot.captured.payload.contains("false"))
    }

    @Test
    fun shouldResolveFriendIdCorrectlyWhenUserIsUserB() {
        val friend = UserEntity(username = "friend", password = "")
        val user = UserEntity(username = "user", password = "")
        val friendship = FriendsEntity(id = FriendsId(friend.id, user.id), userA = friend, userB = user)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs
        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(friend.id to CopyOnWriteArraySet(listOf(session)))

        friendService.notifyFriends(user.id, true)

        verify(exactly = 1) { session.sendMessage(any()) }
    }

    @Test
    fun shouldSkipFriendsWithoutActiveSessions() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf()

        friendService.notifyFriends(user.id, true)

        verify(exactly = 1) { friendsRepository.findFriendsForUser(user.id) }
    }

    @Test
    fun shouldNotSendPresenceUpdateToClosedFriendSession() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")
        val friendship = FriendsEntity(id = FriendsId(user.id, friend.id), userA = user, userB = friend)

        val session = mockk<WebSocketSession>()
        every { session.isOpen } returns false

        every { friendsRepository.findFriendsForUser(user.id) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(friend.id to CopyOnWriteArraySet(listOf(session)))

        friendService.notifyFriends(user.id, false)

        verify(exactly = 1) { session.isOpen }
        verify(exactly = 0) { session.sendMessage(any()) }
    }

    private fun <K, V> concurrentMapOf(vararg pairs: Pair<K, V>): ConcurrentHashMap<K, V> {
        val map = ConcurrentHashMap<K, V>()
        pairs.forEach { (k, v) -> map[k] = v }
        return map
    }
}