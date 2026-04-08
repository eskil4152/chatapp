package com.blikeng.chatapp.serviceTests.roomServiceTests

import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class RoomServiceMembershipTests {
    // ==========================
    // Tests for RoomService room join and leave operations.
    // Verifies:
    // - Joining a room saves the correct UserRoomEntity
    // - Leaving a room deletes the membership
    // - Auth, membership, and UUID validation failure cases
    // ==========================

    @InjectMockKs lateinit var roomService: RoomService

    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRoomRepository: UserRoomRepository
    @MockK private lateinit var friendsService: FriendService
    @MockK private lateinit var bannedUserService: BannedUserService
    @RelaxedMockK private lateinit var eventPublisher: ApplicationEventPublisher

    @AfterEach
    fun clearSecurity() { SecurityContextHolder.clearContext() }

    // ==========================
    // Join room
    // ==========================
    @Test
    fun shouldJoinRoom() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val user = UserEntity(id = userId, username = "u", password = "")

        every { userService.getUserById(userId) } returns user
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.joinRoom(userId, roomId)

        verify(exactly = 1) {
            userRoomRepository.save(match { it.id.userId == userId && it.id.roomId == roomId && it.role == RoomRole.MEMBER })
        }
    }

    @Test
    fun shouldFailToJoinRoomWhenUserNotFound() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userService.getUserById(userId) } returns null

        val exception = assertFailsWith<ApiException> { roomService.joinRoom(userId, roomId) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Leave room
    // ==========================
    @Test
    fun shouldLeaveRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.deleteByIdUserIdAndIdRoomId(any(), any()) } just Runs

        roomService.leaveRoom(UUID.randomUUID().toString())
    }

    @Test
    fun shouldFailToLeaveRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> { roomService.leaveRoom(UUID.randomUUID().toString()) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToLeaveRoomWithInvalidId() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> { roomService.leaveRoom("") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToLeaveRoomWithoutBeingAMember() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val exception = assertFailsWith<ApiException> { roomService.leaveRoom(UUID.randomUUID().toString()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}