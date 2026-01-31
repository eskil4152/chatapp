package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.services.AuthService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.verify
import org.springframework.boot.autoconfigure.condition.ConditionOutcome.match
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@ExtendWith(MockKExtension::class)
class RoomServiceTests {
    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var jwtService: JwtService
    @MockK private lateinit var userRoomRepository: UserRoomRepository

    @InjectMockKs
    lateinit var roomService: RoomService

    @Test
    fun shouldMakeNewRoom(){
        every { jwtService.validateToken(any()) } returns Pair("u", UUID.randomUUID())
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.save(any()) } returns RoomEntity(id = UUID.randomUUID(), name = "r")
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val room = roomService.makeNewRoom("r", "token")
        assertNotNull(room)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidCredentials(){
        every { jwtService.validateToken(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("r", "token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidUser(){
        every { jwtService.validateToken(any()) } returns Pair("u", UUID.randomUUID())
        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("r", "token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidRoomName(){
        val exception = assertFailsWith<ResponseStatusException> {
            roomService.makeNewRoom("", "token")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Invalid name", exception.reason)
    }

    @Test
    fun shouldGetAllRooms(){
        val roomId = UUID.randomUUID()
        val roomEntity = RoomEntity(id = roomId, name = "r")

        every { jwtService.validateToken(any()) } returns Pair("u", UUID.randomUUID())
        every { userRoomRepository.findAllRoomsByUserId(any()) } returns listOf(roomEntity)

        val rooms = roomService.getAllUserRooms("token")
        assertEquals(
            rooms,
            listOf(roomEntity)
        )
    }

    @Test
    fun shouldFailToGetAllRoomsWhenInvalidToken(){
        every { jwtService.validateToken(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.getAllUserRooms("token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldJoinRoom() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val user = UserEntity(
            id = userId,
            username = "u",
            password = ""
        )

        val room = RoomEntity(
            id = roomId,
            name = "r"
        )

        every { jwtService.validateToken("token") } returns ("u" to userId)
        every { userService.getUserById(userId) } returns user
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.joinRoom(roomId, "token")

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
    fun shouldFailToJoinRoomWithInvalidCredentials(){
        every { jwtService.validateToken(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom(UUID.randomUUID(), "token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToJoinRoomWithInvalidUser(){
        every { jwtService.validateToken(any()) } returns Pair("u", UUID.randomUUID())
        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom(UUID.randomUUID(), "token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)
    }

    @Test
    fun shouldFailToJoinRoomWithInvalidRoomId(){
        every { jwtService.validateToken(any()) } returns Pair("u", UUID.randomUUID())
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            roomService.joinRoom(UUID.randomUUID(), "token")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
        assertEquals("Room not found", exception.reason)
    }

    @Test
    fun shouldLeaveRoom(){
        // TODO
    }

    @Test
    fun shouldFailToLeaveRoomWithInvalidCredentials(){
        // TODO
    }

    @Test
    fun shouldFailToLeaveRoomWithInvalidRoomId(){
        // TODO
    }
}