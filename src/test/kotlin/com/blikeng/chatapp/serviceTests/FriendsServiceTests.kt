package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.FriendsService
import com.blikeng.chatapp.services.UserService
import com.blikeng.chatapp.websocket.SessionRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class FriendsServiceTests {
    // ==========================
    // Tests for FriendsService. Verifies:
    // - Retrieving a user's friends
    // - Adding and removing friends
    // - Retrieving friend information
    // - Failure cases for invalid users, missing users, duplicate friendships,
    //   self-add attempts, and non-friend access
    // ==========================

    @InjectMockKs
    private lateinit var friendsService: FriendsService

    @MockK
    private lateinit var friendsRepository: FriendsRepository

    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var presenceHandler: PresenceHandler

    @MockK
    private lateinit var sessionRegistry: SessionRegistry

    private final val objectMapper = ObjectMapper()

    val user1 = UserEntity(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        username = "username1",
        password = "password",
    )
    val user2 = UserEntity(
        id = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
        username = "username2",
        password = "password",
    )

    val friendsEntity = FriendsEntity(
        id = FriendsId(user1.id, user2.id),
        userA = user1,
        userB = user2
    )

    // ==========================
    // Get friends
    // ==========================
    @Test
    fun shouldGetFriendsAsUser1(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { friendsRepository.findFriendsForUser(user1.id) } returns listOf(friendsEntity)
        every { presenceHandler.isUserOnline(user2.id) } returns true

        val friends = friendsService.getFriends()

        assertEquals(friendsEntity.userB.username, friends[0].username)
    }

    @Test
    fun shouldGetFriendsAsUser2(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user2.id, null, emptyList())

        every { userService.getUserById(user2.id) } returns user2
        every { friendsRepository.findFriendsForUser(user2.id) } returns listOf(friendsEntity)
        every { presenceHandler.isUserOnline(user1.id) } returns true

        val friends = friendsService.getFriends()

        assertEquals(friendsEntity.userA.username, friends[0].username)
    }

    @Test
    fun shouldFailToGetFriendsWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriends()
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Add friends
    // ==========================
    @Test
    fun shouldAddFriendsAsUser1(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsEntity>()

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendsService.addFriend("username2")

        val saved = slot.captured

        assertEquals(setOf(user1.id, user2.id), setOf(saved.userA.id, saved.userB.id))
    }

    @Test
    fun shouldAddFriendsAsUser2(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user2.id, null, emptyList())

        val slot = slot<FriendsEntity>()

        every { userService.getUserById(user2.id) } returns user2
        every { userRepository.getUserByUsernameIgnoreCase("username1") } returns user1
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendsService.addFriend("username1")

        val saved = slot.captured

        assertEquals(setOf(user1.id, user2.id), setOf(saved.userA.id, saved.userB.id))
    }

    @Test
    fun shouldFailToAddFriendsWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("username")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddNonExistentUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("non existent") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("non existent")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToAddYourselfAsFriend(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username1") } returns user1

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("username1")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddFriendWhenAlreadyFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("username2")
        }

        assertEquals(HttpStatus.CONFLICT, exception.status)
    }

    // ==========================
    // Remove friends
    // ==========================
    @Test
    fun shouldRemoveFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsId>()

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true
        every { friendsRepository.deleteById(capture(slot)) } just Runs

        friendsService.removeFriend("username2")

        val deleted = slot.captured

        assertEquals(setOf(user1.id, user2.id), setOf(deleted.userA, deleted.userB))
    }

    @Test
    fun shouldFailToRemoveFriendsWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.removeFriend("username")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToRemoveNonExistentUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("fake name") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.removeFriend("fake name")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhoIsNotFriend(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            friendsService.removeFriend("username2")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    // ==========================
    // Get friend info
    // ==========================
    @Test
    fun shouldGetFriendInfo(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true
        every { presenceHandler.isUserOnline(user2.id) } returns true

       val friend = friendsService.getFriendInfo("username2")

        assertEquals(user2.username, friend.username)
        assertEquals(user2.bio, friend.bio)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendInfo("username")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenUserDoesNotExist(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("fake name") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendInfo("fake name")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenNotFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendInfo("username2")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    // ==========================
    // Get friend entity
    // ==========================
    @Test
    fun shouldGetFriendEntity(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

        val friend = friendsService.getFriendEntity("username2", user1.id)

        assertEquals(user2.username, friend.username)
        assertEquals(user2.bio, friend.bio)
    }

    @Test
    fun shouldFailToGetFriendEntityWhenUserDoesNotExist(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("fake name") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendEntity("fake name", user1.id)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetFriendEntityWhenNotFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("real name") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendEntity("real name", user1.id)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    // ==========================
    // Friend updates
    // ==========================
    @Test
    fun shouldNotifyFriendsWhenUserComesOnline() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")

        val userId = user.id
        val friendId = friend.id

        val friendship = FriendsEntity(
            id = FriendsId(userId, friendId),
            userA = user,
            userB = friend
        )

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        every { friendsRepository.findFriendsForUser(userId) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(
            friendId to CopyOnWriteArraySet(listOf(session))
        )

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        friendsService.notifyFriends(userId, true)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains(userId.toString()))
        assertTrue(msgSlot.captured.payload.contains("true"))
    }

    @Test
    fun shouldNotifyFriendsWhenUserGoesOffline() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")

        val userId = user.id
        val friendId = friend.id

        val friendship = FriendsEntity(
            id = FriendsId(userId, friendId),
            userA = user,
            userB = friend
        )

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        every { friendsRepository.findFriendsForUser(userId) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(
            friendId to CopyOnWriteArraySet<WebSocketSession>(listOf(session))
        )

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        friendsService.notifyFriends(userId, false)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains(userId.toString()))
        assertTrue(msgSlot.captured.payload.contains("false"))
    }

    @Test
    fun shouldSkipFriendsWithoutActiveSessions() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")

        val userId = user.id
        val friendId = friend.id

        val friendship = FriendsEntity(
            id = FriendsId(userId, friendId),
            userA = user,
            userB = friend
        )

        every { friendsRepository.findFriendsForUser(userId) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf()

        friendsService.notifyFriends(userId, true)

        verify(exactly = 1) { friendsRepository.findFriendsForUser(userId) }
    }

    @Test
    fun shouldSkipClosedSessionsWhenNotifyingFriends() {
        val user = UserEntity(username = "user", password = "")
        val friend = UserEntity(username = "friend", password = "")

        val userId = user.id
        val friendId = friend.id

        val friendship = FriendsEntity(
            id = FriendsId(userId, friendId),
            userA = user,
            userB = friend
        )

        val closedSession = mockk<WebSocketSession>(relaxed = true)
        every { closedSession.isOpen } returns false

        every { friendsRepository.findFriendsForUser(userId) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(
            friendId to CopyOnWriteArraySet(listOf(closedSession))
        )

        friendsService.notifyFriends(userId, true)

        verify(exactly = 0) { closedSession.sendMessage(any()) }
    }

    @Test
    fun shouldResolveFriendIdCorrectlyWhenUserIsUserB() {
        val friend = UserEntity(username = "friend", password = "")
        val user = UserEntity(username = "user", password = "")

        val userId = user.id
        val friendId = friend.id

        val friendship = FriendsEntity(
            id = FriendsId(friendId, userId),
            userA = friend,
            userB = user
        )

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        every { friendsRepository.findFriendsForUser(userId) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(
            friendId to CopyOnWriteArraySet(listOf(session))
        )

        friendsService.notifyFriends(userId, true)

        verify(exactly = 1) { session.sendMessage(any()) }
    }

    @Test
    fun shouldNotSendPresenceUpdateToClosedFriendSession() {
        val friend = UserEntity(username = "friend", password = "")
        val user = UserEntity(username = "user", password = "")

        val userId = user.id
        val friendId = friend.id

        val friendship = FriendsEntity(
            id = FriendsId(userId, friendId),
            userA = user,
            userB = friend
        )

        val session = mockk<WebSocketSession>()
        every { session.isOpen } returns false

        every { friendsRepository.findFriendsForUser(userId) } returns listOf(friendship)
        every { sessionRegistry.users } returns concurrentMapOf(
            friendId to CopyOnWriteArraySet(listOf(session))
        )

        friendsService.notifyFriends(userId, false)

        verify(exactly = 1) { session.isOpen }
        verify(exactly = 0) { session.sendMessage(any()) }
    }

    private fun <K, V> concurrentMapOf(vararg pairs: Pair<K, V>): ConcurrentHashMap<K, V> {
        val map = ConcurrentHashMap<K, V>()
        pairs.forEach { (k, v) -> map[k] = v }
        return map
    }
}