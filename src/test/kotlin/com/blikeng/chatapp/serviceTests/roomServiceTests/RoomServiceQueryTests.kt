package com.blikeng.chatapp.serviceTests.roomServiceTests

import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class RoomServiceQueryTests {
    // ==========================
    // Tests for RoomService.getAllUsersInRoom.
    // Verifies:
    // - Success: returns users with correct roles
    // - Invalid UUID → 400
    // - User not found → 400
    // - Requester not in room → 404
    // - Requester lacks VIEW_MEMBERS permission (MEMBER role) → 403
    // ==========================

    @InjectMockKs lateinit var roomService: RoomService
    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRoomRepository: UserRoomRepository
    @MockK private lateinit var friendService: FriendService
    @MockK private lateinit var bannedUserService: BannedUserService
    @RelaxedMockK private lateinit var eventPublisher: ApplicationEventPublisher
    @RelaxedMockK private lateinit var redisTemplate: RedisTemplate<String, String>
    @RelaxedMockK private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setupCache() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
    }

    @AfterEach
    fun clearSecurity() { SecurityContextHolder.clearContext() }

    @Test
    fun shouldGetAllUsersInRoom() {
        val userId = UUID.randomUUID()
        val memberId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(id = userId, username = "requester", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MODERATOR, type = RoomType.GROUP)

        val memberRoom = UserRoomEntity(id = UserRoomId(memberId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)
        every { userRoomRepository.findUserRoomsByRoomId(roomId) } returns listOf(memberRoom)
        every { userService.getAllById(listOf(memberId)) } returns listOf(
            UserEntity(id = memberId, username = "member", password = "")
        )

        val result = roomService.getAllUsersInRoom(roomId.toString())

        assertEquals(1, result.size)
        assertEquals("member", result[0].username)
        assertEquals(RoomRole.MEMBER, result[0].role)
    }

    @Test
    fun shouldReturnNullRoleWhenUserNotInRoomMap() {
        val userId = UUID.randomUUID()
        val mappedUserId = UUID.randomUUID()
        val unmappedUserId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(id = userId, username = "requester", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MODERATOR, type = RoomType.GROUP)

        val memberRoom = UserRoomEntity(id = UserRoomId(mappedUserId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)
        every { userRoomRepository.findUserRoomsByRoomId(roomId) } returns listOf(memberRoom)
        every { userService.getAllById(listOf(mappedUserId)) } returns listOf(
            UserEntity(id = unmappedUserId, username = "ghost", password = "")
        )

        val result = roomService.getAllUsersInRoom(roomId.toString())

        assertEquals(1, result.size)
        assertNull(result[0].role)
    }

    @Test
    fun shouldFailToGetUsersInRoomWithInvalidUUID() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> { roomService.getAllUsersInRoom("not-a-uuid") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetUsersInRoomWhenNotMember() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns null

        val exception = assertFailsWith<ApiException> { roomService.getAllUsersInRoom(roomId.toString()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetUsersInRoomWhenNotPermitted() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

        val exception = assertFailsWith<ApiException> { roomService.getAllUsersInRoom(roomId.toString()) }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }
}
