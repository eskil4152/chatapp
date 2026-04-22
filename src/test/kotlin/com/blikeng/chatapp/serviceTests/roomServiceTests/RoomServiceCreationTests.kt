package com.blikeng.chatapp.serviceTests.roomServiceTests

import com.blikeng.chatapp.dtos.room.JoinedRoomDTO
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.repositories.PrivateRoomPartner
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows

@ExtendWith(MockKExtension::class)
class RoomServiceCreationTests {
    // ==========================
    // Tests for RoomService room creation and retrieval.
    // Verifies:
    // - Creating plain and encrypted rooms
    // - Retrieving a user's room list including private room name resolution
    // - Auth and validation failure cases
    // ==========================

    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRoomRepository: UserRoomRepository
    @MockK private lateinit var friendsService: FriendService
    @MockK private lateinit var bannedUserService: BannedUserService
    @MockK private lateinit var eventPublisher: ApplicationEventPublisher
    @RelaxedMockK private lateinit var redisTemplate: RedisTemplate<String, String>
    @RelaxedMockK private lateinit var objectMapper: ObjectMapper

    @InjectMockKs lateinit var roomService: RoomService

    @BeforeEach
    fun setupCache() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
    }

    @AfterEach
    fun clearSecurity() { SecurityContextHolder.clearContext() }

    // ==========================
    // Create rooms
    // ==========================
    @Test
    fun shouldMakeNewRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomSlot = slot<RoomEntity>()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.save(capture(roomSlot)) } answers {
            val r = roomSlot.captured
            RoomEntity(id = UUID.randomUUID(), name = r.name, encrypted = r.encrypted, keyVersion = r.keyVersion, type = RoomType.GROUP)
        }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.makeNewRoom("r", false)

        assertEquals("r", roomSlot.captured.name)
        assertEquals(false, roomSlot.captured.encrypted)
        assertNull(roomSlot.captured.keyVersion)
    }

    @Test
    fun shouldMakeNewEncryptedRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomSlot = slot<RoomEntity>()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.save(capture(roomSlot)) } answers {
            val r = roomSlot.captured
            RoomEntity(id = UUID.randomUUID(), name = r.name, encrypted = r.encrypted, keyVersion = r.keyVersion, type = RoomType.GROUP)
        }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.makeNewRoom("r", true)

        assertEquals("r", roomSlot.captured.name)
        assertEquals(true, roomSlot.captured.encrypted)
        assertNotNull(roomSlot.captured.keyVersion)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidRoomName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertThrows<ApiException> { roomService.makeNewRoom("", false) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_ROOM_NAME, exception.message)
    }

    @Test
    fun shouldFailToMakeRoomWithoutRoomName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertThrows<ApiException> { roomService.makeNewRoom(null, false) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_ROOM_NAME, exception.message)
    }

    @Test
    fun shouldFailToMakeRoomWithTooLongName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertThrows<ApiException> { roomService.makeNewRoom("a".repeat(101), false) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_ROOM_NAME, exception.message)
    }

    @Test
    fun shouldMakeRoomWithTrimmedName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomSlot = slot<RoomEntity>()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.save(capture(roomSlot)) } answers {
            val r = roomSlot.captured
            RoomEntity(id = UUID.randomUUID(), name = r.name, encrypted = r.encrypted, keyVersion = r.keyVersion, type = RoomType.GROUP)
        }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.makeNewRoom("  my room  ", false)

        assertEquals("my room", roomSlot.captured.name)
    }

    @Test
    fun shouldFailToMakeRoomWhenNoAuthentication() {
        val exception = assertThrows<ApiException> { roomService.makeNewRoom("roomName", false) }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    // ==========================
    // Get user rooms
    // ==========================
    @Test
    fun shouldGetAllRooms() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val secondRoom = RoomEntity(name = "r2", type = RoomType.PRIVATE)

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findRoomsForUser(any()) } returns listOf(
            JoinedRoomDTO(room, RoomRole.OWNER),
            JoinedRoomDTO(secondRoom, RoomRole.OWNER, type = RoomType.PRIVATE)
        )
        val partner = mockk<PrivateRoomPartner>()
        every { partner.roomId } returns secondRoom.id
        every { partner.username } returns "su"
        every { userRoomRepository.findOtherUsersInPrivateRooms(listOf(secondRoom.id), any()) } returns listOf(partner)

        val rooms = roomService.getAllUserRooms()
        assertEquals(
            listOf(
                RoomDTO(roomId = room.id.toString(), roomName = room.name, encrypted = room.encrypted, role = RoomRole.OWNER, type = room.type),
                RoomDTO(roomId = secondRoom.id.toString(), roomName = "su", encrypted = secondRoom.encrypted, role = RoomRole.OWNER, type = secondRoom.type)
            ),
            rooms
        )
    }

    @Test
    fun shouldFailToGetRoomsWhenNoAuthentication() {
        val exception = assertThrows<ApiException> { roomService.getAllUserRooms() }
        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldNamePrivateRoomAsErrorWhenOtherUserIsMissing() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val room = RoomEntity(name = "r", type = RoomType.PRIVATE)
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findRoomsForUser(any()) } returns listOf(JoinedRoomDTO(room, RoomRole.OWNER, type = RoomType.PRIVATE))
        every { userRoomRepository.findOtherUsersInPrivateRooms(any(), any()) } returns emptyList()

        val rooms = roomService.getAllUserRooms()
        assertEquals(
            listOf(RoomDTO(roomId = room.id.toString(), roomName = "Error", encrypted = room.encrypted, role = RoomRole.OWNER, type = room.type)),
            rooms
        )
    }

    @Test
    fun shouldReturnCachedRooms() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        val cached = listOf(RoomDTO(roomId = UUID.randomUUID().toString(), roomName = "cached", encrypted = false, role = RoomRole.MEMBER, type = RoomType.GROUP))
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get("user:$userId:rooms") } returns "cached-json"
        every { redisTemplate.opsForValue() } returns ops
        every { objectMapper.readValue("cached-json", any<com.fasterxml.jackson.core.type.TypeReference<List<RoomDTO>>>()) } returns cached

        val result = roomService.getAllUserRooms()

        assertEquals(cached, result)
        verify(exactly = 0) { roomRepository.findRoomsForUser(any()) }
    }
}