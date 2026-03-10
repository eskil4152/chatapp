package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.JoinedRoom
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
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

        val exception = assertFailsWith<ApiException> {
            roomService.makeNewRoom("r", false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_USER, exception.message)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidRoomName(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.makeNewRoom("", false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_ROOM_NAME, exception.message)
    }

    @Test
    fun shouldFailToMakeRoomWithoutRoomName(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.makeNewRoom(null, false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_ROOM_NAME, exception.message)
    }

    @Test
    fun shouldFailToMakeRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.makeNewRoom("wrong name", false)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
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
                    role = RoomRole.OWNER,
                    type = RoomType.GROUP
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

        val exception = assertFailsWith<ApiException> {
            roomService.joinRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_USER, exception.message)
    }

    @Test
    fun shouldFailToJoinNonExistingRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            roomService.joinRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, exception.message)
    }

    @Test
    fun shouldFailToGetRoomsWhenNoAuthentication() {
        val exception = assertFailsWith<ApiException> {
            roomService.getAllUserRooms()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToGetRoomsWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.getAllUserRooms()
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToMakeRoomWhenNoAuthentication() {
        val exception = assertFailsWith<ApiException> {
            roomService.makeNewRoom("roomName", false)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToJoinRoomWhenNoAuthentication() {
        val exception = assertFailsWith<ApiException> {
            roomService.joinRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToJoinRoomWithInvalidId(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            roomService.joinRoom("not a real UUID")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_UUID, exception.message)
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

        val exception = assertFailsWith<ApiException> {
            roomService.leaveRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToLeaveRoomWithInvalidId(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.leaveRoom("")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToLeaveRoomWithoutBeingAMember(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val exception = assertFailsWith<ApiException> {
            roomService.leaveRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
        )

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
        )

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
        )

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
        )

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.status)
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
        )

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
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
            role = RoomRole.OWNER,
            type = RoomType.GROUP
        )

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(roomDTO)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToEditRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(UUID.randomUUID().toString(), "new room name", false, RoomRole.OWNER, RoomType.GROUP))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
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

        val exception = assertFailsWith<ApiException> {
            roomService.deleteRoom(roomId.toString())
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWithoutBeingOwner(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER)

        val exception = assertFailsWith<ApiException> {
            roomService.deleteRoom(roomId.toString())
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWithInvalidId(){
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.deleteRoom("not a real UUID")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.deleteRoom(UUID.randomUUID().toString())
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }
}