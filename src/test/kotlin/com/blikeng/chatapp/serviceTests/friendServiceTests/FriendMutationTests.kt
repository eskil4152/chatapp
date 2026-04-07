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
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.Runs
import io.mockk.slot
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
class FriendMutationTests {
    // ==========================
    // Tests for FriendsService add and remove operations.
    // Verifies:
    // - Adding a friend saves the correct FriendsEntity
    // - Removing a friend deletes the correct FriendsId
    // - Failure cases for invalid user, missing user, self-add,
    //   duplicate friendship, and non-friend removal
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

    // ==========================
    // Add friends
    // ==========================
    @Test
    fun shouldAddFriendsAsUser1() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsEntity>()
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.sendFriendRequest("username2")

        assertEquals(setOf(user1.id, user2.id), setOf(slot.captured.userA.id, slot.captured.userB.id))
    }

    @Test
    fun shouldAddFriendsAsUser2() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user2.id, null, emptyList())

        val slot = slot<FriendsEntity>()
        every { userService.getUserById(user2.id) } returns user2
        every { userRepository.getUserByUsernameIgnoreCase("username1") } returns user1
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.sendFriendRequest("username1")

        assertEquals(setOf(user1.id, user2.id), setOf(slot.captured.userA.id, slot.captured.userB.id))
    }

    @Test
    fun shouldOrderUsersCorrectlyWhenAddingFriendAndCurrentUserIdIsLower() {
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        val lowUser = UserEntity(id = lowId, username = "low", password = "password")
        val highUser = UserEntity(id = highId, username = "high", password = "password")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(lowId, null, emptyList())

        val slot = slot<FriendsEntity>()
        every { userService.getUserById(lowId) } returns lowUser
        every { userRepository.getUserByUsernameIgnoreCase("high") } returns highUser
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.sendFriendRequest("high")

        assertEquals(lowId, slot.captured.userA.id)
        assertEquals(highId, slot.captured.userB.id)
    }

    @Test
    fun shouldOrderUsersCorrectlyWhenAddingFriendAndCurrentUserIdIsHigher() {
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        val lowUser = UserEntity(id = lowId, username = "low", password = "password")
        val highUser = UserEntity(id = highId, username = "high", password = "password")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(highId, null, emptyList())

        val slot = slot<FriendsEntity>()
        every { userService.getUserById(highId) } returns highUser
        every { userRepository.getUserByUsernameIgnoreCase("low") } returns lowUser
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.sendFriendRequest("low")

        assertEquals(lowId, slot.captured.userA.id)
        assertEquals(highId, slot.captured.userB.id)
    }

    @Test
    fun shouldFailToAddFriendsWithInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> { friendService.sendFriendRequest("username") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddNonExistentUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("non existent") } returns null

        val exception = assertFailsWith<ApiException> { friendService.sendFriendRequest("non existent") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToAddYourselfAsFriend() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username1") } returns user1

        val exception = assertFailsWith<ApiException> { friendService.sendFriendRequest("username1") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddFriendWhenAlreadyFriends() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

        val exception = assertFailsWith<ApiException> { friendService.sendFriendRequest("username2") }
        assertEquals(HttpStatus.CONFLICT, exception.status)
    }

    // ==========================
    // Remove friends
    // ==========================
    @Test
    fun shouldRemoveFriends() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsId>()
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true
        every { friendsRepository.deleteById(capture(slot)) } just Runs

        friendService.removeFriend("username2")

        assertEquals(setOf(user1.id, user2.id), setOf(slot.captured.userA, slot.captured.userB))
    }

    @Test
    fun shouldOrderUsersCorrectlyWhenRemovingFriendAndCurrentUserIdIsHigher() {
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        val lowUser = UserEntity(id = lowId, username = "low", password = "password")
        val highUser = UserEntity(id = highId, username = "high", password = "password")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(highId, null, emptyList())

        val slot = slot<FriendsId>()
        every { userService.getUserById(highId) } returns highUser
        every { userRepository.getUserByUsernameIgnoreCase("low") } returns lowUser
        every { friendsRepository.existsById(any()) } returns true
        every { friendsRepository.deleteById(capture(slot)) } just Runs

        friendService.removeFriend("low")

        assertEquals(lowId, slot.captured.userA)
        assertEquals(highId, slot.captured.userB)
    }

    @Test
    fun shouldFailToRemoveFriendsWithInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> { friendService.removeFriend("username") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToRemoveNonExistentUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("fake name") } returns null

        val exception = assertFailsWith<ApiException> { friendService.removeFriend("fake name") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhoIsNotFriend() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> { friendService.removeFriend("username2") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}