package com.blikeng.chatapp.serviceTests.friendServiceTests

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.UserService
import com.blikeng.chatapp.websocket.SessionRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class FriendQueryTests {
    // ==========================
    // Tests for FriendsService read operations.
    // Verifies:
    // - Getting a user's friend list from either side of the friendship
    // - Getting friend info and friend entity by username
    // - Failure cases for invalid user, missing user, and non-friend access
    // ==========================

    @InjectMockKs private lateinit var friendService: FriendService
    @MockK private lateinit var friendsRepository: FriendsRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK private lateinit var presenceHandler: PresenceHandler
    private val objectMapper = ObjectMapper()

    val user1 = UserEntity(id = UUID.randomUUID(), username = "username1", password = "password")
    val user2 = UserEntity(id = UUID.randomUUID(), username = "username2", password = "password")
    val friendsEntity = FriendsEntity(id = FriendsId(user1.id, user2.id), userA = user1, userB = user2)

    // ==========================
    // Get friends
    // ==========================
    @Test
    fun shouldGetFriendsAsUser1() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { friendsRepository.findFriendsForUser(user1.id) } returns listOf(friendsEntity)
        every { presenceHandler.isUserOnline(user2.id) } returns true

        val friends = friendService.getFriends()

        assertEquals(friendsEntity.userB.username, friends[0].username)
    }

    @Test
    fun shouldGetFriendsAsUser2() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user2.id, null, emptyList())

        every { userService.getUserById(user2.id) } returns user2
        every { friendsRepository.findFriendsForUser(user2.id) } returns listOf(friendsEntity)
        every { presenceHandler.isUserOnline(user1.id) } returns true

        val friends = friendService.getFriends()

        assertEquals(friendsEntity.userA.username, friends[0].username)
    }

    @Test
    fun shouldFailToGetFriendsWithInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> { friendService.getFriends() }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Get friend info
    // ==========================
    @Test
    fun shouldGetFriendInfo() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true
        every { presenceHandler.isUserOnline(user2.id) } returns true

        val friend = friendService.getFriendInfo("username2")

        assertEquals(user2.username, friend.username)
        assertEquals(user2.bio, friend.bio)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> { friendService.getFriendInfo("username") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenUserDoesNotExist() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("fake name") } returns null

        val exception = assertFailsWith<ApiException> { friendService.getFriendInfo("fake name") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenNotFriends() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> { friendService.getFriendInfo("username2") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    // ==========================
    // Get friend entity
    // ==========================
    @Test
    fun shouldGetFriendEntity() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

        val friend = friendService.getFriendEntity("username2", user1.id)

        assertEquals(user2.username, friend.username)
        assertEquals(user2.bio, friend.bio)
    }

    @Test
    fun shouldFailToGetFriendEntityWhenUserDoesNotExist() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userRepository.getUserByUsernameIgnoreCase("fake name") } returns null

        val exception = assertFailsWith<ApiException> { friendService.getFriendEntity("fake name", user1.id) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetFriendEntityWhenNotFriends() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userRepository.getUserByUsernameIgnoreCase("real name") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> { friendService.getFriendEntity("real name", user1.id) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}