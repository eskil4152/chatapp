package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.ErrorMessages.INVALID_ROOM_ID
import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.JoinedRoom
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
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
    @MockK private lateinit var chatRepository: ChatRepository

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

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("", false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Invalid room name", exception.reason)
    }

    @Test
    fun shouldFailToMakeRoomWithoutRoomName(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom(null, false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Invalid room name", exception.reason)
    }

    @Test
    fun shouldFailToMakeRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("rong name", false)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }

    @Test
    fun shouldGetAllRooms(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val roomId = UUID.randomUUID()
        val room = RoomEntity(id = roomId, name = "r")
        val joinedRoom = JoinedRoom(room, RoomRole.OWNER)

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
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
    fun shouldFailToGetRoomsWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.getAllUserRooms()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
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
        assertEquals(INVALID_ROOM_ID, exception.reason)
    }

    @Test
    fun shouldLeaveRoom(){
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

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.leaveRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }

    @Test
    fun shouldFailToLeaveRoomWithInvalidId(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.leaveRoom("")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun shouldFailToLeaveRoomWithoutBeingAMember(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.leaveRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun shouldEditRoomName(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = roomId.toString(),
            roomName = "newName",
            encrypted = false,
            role = RoomRole.OWNER
        )

        val room = RoomEntity(id = roomId, name = "r")

        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER)

        every { roomRepository.findById(roomId) } returns Optional.of(room)
        every { roomRepository.save(any()) } answers { firstArg() }

        roomService.editRoom(roomDTO)

        verify {
            roomRepository.save(match { it.name == "newName" && it.id == roomId })
        }
    }

    @Test
    fun shouldFailToEditRoomNameWithoutName(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = UUID.randomUUID().toString(),
            encrypted = false,
            roomName = null,
            role = RoomRole.OWNER
        )

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun shouldFailToEditRoomNameWithInvalidName(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = UUID.randomUUID().toString(),
            encrypted = false,
            roomName = "",
            role = RoomRole.OWNER
        )

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun shouldFailToEditRoomWithoutBeingInRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns null
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = UUID.randomUUID().toString(),
            encrypted = false,
            roomName = "real name",
            role = RoomRole.OWNER
        )

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun shouldFailToEditRoomWithoutBeingOwner(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER)

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = UUID.randomUUID().toString(),
            encrypted = false,
            roomName = "real name",
            role = RoomRole.OWNER
        )

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun shouldFailToEditRoomIfNotFoundInDatabase(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER)
        every { roomRepository.findById(roomId) } returns Optional.empty()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = roomId.toString(),
            encrypted = false,
            roomName = "real name",
            role = RoomRole.OWNER
        )

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun shouldFailToEditRoomWithInvalidId(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val roomDTO = RoomDTO(
            roomId = "not a real UUID",
            encrypted = false,
            roomName = "new room name",
            role = RoomRole.OWNER
        )

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun shouldFailToEditRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.editRoom(RoomDTO(UUID.randomUUID().toString(), "new room name", false, RoomRole.OWNER))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }

    @Test
    fun shouldDeleteRoom(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER)

        every { roomRepository.deleteById(roomId) } just Runs
        every { userRoomRepository.deleteAllByIdRoomId(roomId) } just Runs

        roomService.deleteRoom(roomId.toString())

        verify(exactly = 1) {
            roomRepository.deleteById(roomId)
            userRoomRepository.deleteAllByIdRoomId(roomId)
        }
    }

    @Test
    fun shouldFailToDeleteRoomWithoutBeingInRoom(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.deleteRoom(roomId.toString())
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun shouldFailToDeleteRoomWithoutBeingOwner(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER)

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.deleteRoom(roomId.toString())
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
    }

    @Test
    fun shouldFailToDeleteRoomWithInvalidId(){
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.deleteRoom("not a real UUID")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
    }

    @Test
    fun shouldFailToDeleteRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.deleteRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
    }
}