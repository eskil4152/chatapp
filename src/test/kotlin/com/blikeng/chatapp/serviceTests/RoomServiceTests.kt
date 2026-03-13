package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.repositories.JoinedRoom
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.FriendsService
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
    // ==========================
    // Tests for RoomService. Verifies:
    // - Creating normal and encrypted rooms
    // - Retrieving a user's rooms
    // - Joining and leaving rooms
    // - Editing and deleting rooms
    // - Creating and retrieving private message rooms
    // - Failure cases for invalid users, invalid room data,
    //   invalid room IDs, missing membership, and missing authentication
    // ==========================

    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRoomRepository: UserRoomRepository
    @MockK private lateinit var friendService: FriendsService

    @InjectMockKs
    lateinit var roomService: RoomService

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
    }

    // ==========================
    // Create rooms
    // ==========================
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
                keyVersion = r.keyVersion,
                type = RoomType.GROUP
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
                keyVersion = r.keyVersion,
                type = RoomType.GROUP
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
    fun shouldFailToMakeRoomWhenNoAuthentication() {
        val exception = assertFailsWith<ApiException> {
            roomService.makeNewRoom("roomName", false)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    // ==========================
    // Get user rooms
    // ==========================
    @Test
    fun shouldGetAllRooms(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val joinedRoom = JoinedRoom(room, RoomRole.OWNER)

        val secondRoom = RoomEntity(name = "r2", type = RoomType.PRIVATE)
        val joinedRoom2 = JoinedRoom(secondRoom, RoomRole.OWNER, type = RoomType.PRIVATE)

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findRoomsForUser(any()) } returns listOf(joinedRoom, joinedRoom2)
        every { userRoomRepository.findOtherUser(secondRoom.id, any()) } returns UserEntity(username = "su", password = "")

        val rooms = roomService.getAllUserRooms()
        assertEquals(
            listOf(
                RoomDTO(
                    roomId = room.id.toString(),
                    roomName = room.name,
                    encrypted = room.encrypted,
                    role = RoomRole.OWNER,
                    type = room.type,
                ),
                RoomDTO(
                    roomId = secondRoom.id.toString(),
                    roomName = secondRoom.name,
                    encrypted = secondRoom.encrypted,
                    role = RoomRole.OWNER,
                    type = secondRoom.type,
                )
            ),
            rooms
        )
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

    // ==========================
    // Join and leave rooms
    // ==========================
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
            name = "r",
            type = RoomType.GROUP
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

    // ==========================
    // Edit rooms
    // ==========================
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

        val room = RoomEntity(id = roomId, name = "r", type = RoomType.GROUP)

        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)

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
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

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
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
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

    // ==========================
    // Delete rooms
    // ==========================
    @Test
    fun shouldDeleteRoom(){
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)

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

        every { userService.getUserById(userId) } returns
                UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
                UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

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

    // ==========================
    // Private message rooms
    // ==========================
    @Test
    fun shouldCreatePrivateMessageRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntity("us", any()) } returns UserEntity(username = "us", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()

        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val roomId = roomService.getOrStartPrivateMessage("us")

        assertNotNull(roomId)
        verify(exactly = 1) {roomRepository.save(any())}
        verify(exactly = 2) {userRoomRepository.save(any())}
    }

    @Test
    fun shouldGetPrivateMessageRoom(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntity("us", any()) } returns UserEntity(username = "us", password = "")
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "room", type = RoomType.GROUP))

        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val roomId = roomService.getOrStartPrivateMessage("us")

        assertNotNull(roomId)
        verify(exactly = 0) {roomRepository.save(any())}
        verify(exactly = 0) {userRoomRepository.save(any())}
    }

    @Test
    fun shouldFailToGetPrivateMessagesWhenInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.getOrStartPrivateMessage("some user")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetPrivateMessagesWhenFriendNotFound(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntity("some user", any()) } throws UserNotFoundException()

        val exception = assertFailsWith<ApiException> {
            roomService.getOrStartPrivateMessage("some user")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetPrivateMessagesWithYourself(){
        val user = UserEntity(username = "u", password = "")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user.id, null, emptyList())

        every { userService.getUserById(user.id) } returns user
        every { friendService.getFriendEntity("u", any()) } returns user
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            roomService.getOrStartPrivateMessage("u")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldGenerateSamePrivateRoomForBothUserOrders() {
        val low = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

        val userLow = UserEntity(id = low, username = "low", password = "")
        val userHigh = UserEntity(id = high, username = "high", password = "")

        every { userService.getUserById(low) } returns userLow
        every { userService.getUserById(high) } returns userHigh

        every { friendService.getFriendEntity("high", low) } returns userHigh
        every { friendService.getFriendEntity("low", high) } returns userLow

        every { roomRepository.findById(any()) } returns Optional.empty()
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(low, null, emptyList())
        val roomId1 = roomService.getOrStartPrivateMessage("high")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(high, null, emptyList())
        val roomId2 = roomService.getOrStartPrivateMessage("low")

        assertEquals(roomId1, roomId2)
    }

    @Test
    fun shouldNamePrivateRoomAsErrorWhenOtherUserIsMissing(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val room = RoomEntity(name = "r", type = RoomType.PRIVATE)
        val joinedRoom = JoinedRoom(room, RoomRole.OWNER, type = RoomType.PRIVATE)

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findRoomsForUser(any()) } returns listOf(joinedRoom)
        every { userRoomRepository.findOtherUser(any(), any()) } returns null

        val rooms = roomService.getAllUserRooms()
        assertEquals(
            listOf(
                RoomDTO(
                    roomId = room.id.toString(),
                    roomName = "Error",
                    encrypted = room.encrypted,
                    role = RoomRole.OWNER,
                    type = room.type,
                ),
            ),
            rooms
        )
    }
}