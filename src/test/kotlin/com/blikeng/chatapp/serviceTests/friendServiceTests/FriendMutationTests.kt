package com.blikeng.chatapp.serviceTests.friendServiceTests

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.notifications.events.FriendRemovedEvent
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class FriendMutationTests {
    // ==========================
    // Tests for FriendsService add and remove operations.
    // Verifies:
    // - Adding a friend saves the correct FriendsEntity
    // - Removing a friend deletes the correct FriendsId
    // - Failure cases for invalid user, missing user, and non-friend removal
    // ==========================

    @InjectMockKs private lateinit var friendService: FriendService

    @MockK private lateinit var friendsRepository: FriendsRepository

    @MockK private lateinit var userService: UserService

    @MockK private lateinit var userRepository: UserRepository

    @MockK private lateinit var eventPublisher: ApplicationEventPublisher

    @MockK private lateinit var presenceHandler: PresenceHandler

    @RelaxedMockK private lateinit var redisTemplate: RedisTemplate<String, String>

    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setupCache() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
    }

    val user1 = UserEntity(id = UUID.randomUUID(), username = "username1", password = "password")
    val user2 = UserEntity(id = UUID.randomUUID(), username = "username2", password = "password")

    // ==========================
    // Add friends
    // ==========================
    @Test
    fun shouldAddFriendsAsUser1() {
        val slot = slot<FriendsEntity>()
        every { userService.getUserById(user1.id) } returns user1
        every { userService.getUserById(user2.id) } returns user2
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.addFriend(user1.id, user2.id)

        assertEquals(setOf(user1.id, user2.id), setOf(slot.captured.userA.id, slot.captured.userB.id))
    }

    @Test
    fun shouldAddFriendsAsUser2() {
        val slot = slot<FriendsEntity>()
        every { userService.getUserById(user2.id) } returns user2
        every { userService.getUserById(user1.id) } returns user1
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.addFriend(user2.id, user1.id)

        assertEquals(setOf(user1.id, user2.id), setOf(slot.captured.userA.id, slot.captured.userB.id))
    }

    @Test
    fun shouldOrderUsersCorrectlyWhenAddingFriendAndCurrentUserIdIsLower() {
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        val lowUser = UserEntity(id = lowId, username = "low", password = "password")
        val highUser = UserEntity(id = highId, username = "high", password = "password")

        val slot = slot<FriendsEntity>()
        every { userService.getUserById(lowId) } returns lowUser
        every { userService.getUserById(highId) } returns highUser
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.addFriend(lowId, highId)

        assertEquals(lowId, slot.captured.userA.id)
        assertEquals(highId, slot.captured.userB.id)
    }

    @Test
    fun shouldOrderUsersCorrectlyWhenAddingFriendAndCurrentUserIdIsHigher() {
        val lowId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val highId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        val lowUser = UserEntity(id = lowId, username = "low", password = "password")
        val highUser = UserEntity(id = highId, username = "high", password = "password")

        val slot = slot<FriendsEntity>()
        every { userService.getUserById(highId) } returns highUser
        every { userService.getUserById(lowId) } returns lowUser
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendService.addFriend(highId, lowId)

        assertEquals(lowId, slot.captured.userA.id)
        assertEquals(highId, slot.captured.userB.id)
    }

    @Test
    fun shouldFailToAddFriendsWithInvalidUser() {
        every { userService.getUserById(user1.id) } returns null

        val exception = assertThrows<ApiException> { friendService.addFriend(user1.id, user2.id) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddNonExistentFriend() {
        every { userService.getUserById(user1.id) } returns user1
        every { userService.getUserById(user2.id) } returns null

        val exception = assertThrows<ApiException> { friendService.addFriend(user1.id, user2.id) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddYourselfAsFriend() {
        val exception = assertThrows<ApiException> { friendService.addFriend(user1.id, user1.id) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
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
        every { userRepository.findById(user2.id) } returns Optional.of(user2)
        every { friendsRepository.existsById(any()) } returns true
        every { friendsRepository.deleteById(capture(slot)) } just Runs
        every { redisTemplate.convertAndSend(any<String>(), any<String>()) } returns 1L
        every { eventPublisher.publishEvent(any<FriendRemovedEvent>()) } just Runs

        friendService.removeFriend(UserIdDTO(user2.id.toString()))

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
        every { userRepository.findById(lowId) } returns Optional.of(lowUser)
        every { friendsRepository.existsById(any()) } returns true
        every { friendsRepository.deleteById(capture(slot)) } just Runs
        every { redisTemplate.convertAndSend(any<String>(), any<String>()) } returns 1L
        every { eventPublisher.publishEvent(any<FriendRemovedEvent>()) } just Runs

        friendService.removeFriend(UserIdDTO(lowId.toString()))

        assertEquals(lowId, slot.captured.userA)
        assertEquals(highId, slot.captured.userB)
    }

    @Test
    fun shouldFailToRemoveNonExistentUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.findById(user2.id) } returns Optional.empty()

        val exception = assertThrows<ApiException> { friendService.removeFriend(UserIdDTO(user2.id.toString())) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhoIsNotFriend() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.findById(user2.id) } returns Optional.of(user2)
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertThrows<ApiException> { friendService.removeFriend(UserIdDTO(user2.id.toString())) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToRemoveFriendWithInvalidUUID() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1

        val exception = assertThrows<ApiException> { friendService.removeFriend(UserIdDTO("not-a-uuid")) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }
}
