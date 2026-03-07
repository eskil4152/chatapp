package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.ErrorMessages.INVALID_ROOM_NAME
import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.JoinedRoom
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class RoomServiceTests {
    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRoomRepository: UserRoomRepository

    @InjectMockKs
    lateinit var roomService: RoomService

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun shouldMakeNewRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomSlot = slot<RoomEntity>()

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.save(capture(roomSlot)) } answers {
            val r = roomSlot.captured
            RoomEntity(
                id = UUID.randomUUID(),
                name = r.name,
                encrypted = r.encrypted,
                keyVersion = r.keyVersion
            )
        }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.makeNewRoom("r", false)

        val room = roomSlot.captured
        assertEquals("r", room.name)
        assertEquals(false, room.encrypted)
        assertNull(room.keyVersion)
    }

    @Test
    fun shouldMakeNewEncryptedRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomSlot = slot<RoomEntity>()

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.save(capture(roomSlot)) } answers {
            val r = roomSlot.captured
            RoomEntity(
                id = UUID.randomUUID(),
                name = r.name,
                encrypted = r.encrypted,
                keyVersion = r.keyVersion
            )
        }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.makeNewRoom("r", true)

        val room = roomSlot.captured
        assertEquals("r", room.name)
        assertEquals(true, room.encrypted)
        assertNotNull(room.keyVersion)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("r", false)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidRoomName(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("", false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Invalid room name", exception.reason)
    }

    @Test
    fun shouldGetAllRooms(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomId = UUID.randomUUID()
        val room = RoomEntity(id = roomId, name = "r")
        val joinedRoom = JoinedRoom(room, RoomRole.OWNER)

        every { roomRepository.findRoomsForUser(any()) } returns listOf(joinedRoom)

        val rooms = roomService.getAllUserRooms()
        assertEquals(
            listOf(
                RoomDTO(
                    roomId = roomId.toString(),
                    roomName = "r",
                    encrypted = room.encrypted,
                    role = RoomRole.OWNER
                )
            ),
            rooms
        )
    }

    @Test
    fun shouldJoinRoom() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        val user = UserEntity(
            id = userId,
            username = "u",
            password = ""
        )

        val room = RoomEntity(
            id = roomId,
            name = "r"
        )

        every { userService.getUserById(userId) } returns user
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.joinRoom(roomId.toString())

        verify(exactly = 1) {
            userRoomRepository.save(
                match {
                    it.id.userId == userId &&
                            it.id.roomId == roomId &&
                            it.role == RoomRole.MEMBER
                }
            )
        }
    }

    @Test
    fun shouldFailToJoinRoomWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)
    }

    @Test
    fun shouldFailToJoinNonExistingRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        assertEquals("Room not found", exception.reason)
    }

    @Test
    fun shouldFailToGetRoomsWhenNoAuthentication() {
        val exception = assertFailsWith<ResponseStatusException> {
            roomService.getAllUserRooms()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToMakeRoomWhenNoAuthentication() {
        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("roomName", false)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToJoinRoomWhenNoAuthentication() {
        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToJoinRoomWithInvalidId(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom("not a real UUID")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals(INVALID_ROOM_NAME, exception.reason)
    }
}