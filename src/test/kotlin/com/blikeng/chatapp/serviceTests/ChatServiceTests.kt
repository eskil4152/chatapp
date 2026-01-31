package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.DTOs.ChangeUserDTO
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.UserService
import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ChatServiceTests {
    private lateinit var chatService: ChatService

    @BeforeEach
    fun setUp() {
        chatService = ChatService()
    }

    @Test
    fun shouldRegisterSession(){
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.registerSession(userId, session)

        assertEquals(session, chatService.users[userId])
    }

    @Test
    fun shouldRemoveSession(){
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        chatService.registerSession(userId, session)

        assertEquals(session, chatService.users[userId])
        chatService.removeSession(userId, session)
        assertNotEquals(session, chatService.users[userId])
    }

    @Test
    fun shouldRemoveSessionForEveryRoom(){
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val roomId = UUID.randomUUID()
        val roomId2 = UUID.randomUUID()

        chatService.registerSession(userId, session)
        chatService.joinRoom(roomId, session)
        chatService.joinRoom(roomId2, session)

        assertEquals(session, chatService.users[userId])
        assertEquals(chatService.rooms[roomId]?.first(), session)
        assertEquals(chatService.rooms[roomId2]?.first(), session)

        chatService.removeSession(userId, session)

        assertNull(chatService.users[userId])
        assertTrue { chatService.rooms[roomId]?.isEmpty() == true }
        assertTrue { chatService.rooms[roomId2]?.isEmpty() == true }
    }

    @Test
    fun shouldJoinRoom(){
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.joinRoom(roomId, session)

        assertEquals(session, chatService.rooms[roomId]?.first())
    }

    @Test
    fun shouldLeaveRoom(){
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.joinRoom(roomId, session)
        assertEquals(session, chatService.rooms[roomId]?.first())

        chatService.leaveRoom(roomId, session)
        assertTrue { chatService.rooms[roomId]?.isEmpty() == true }
    }

    @Test
    fun shouldBroadcastMessageToAllSessionsInRoom() {
        val roomId = UUID.randomUUID()
        val message = TextMessage("hello")

        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)

        chatService.joinRoom(roomId, session1)
        chatService.joinRoom(roomId, session2)

        chatService.broadcast(roomId, message)

        verify(exactly = 1) { session1.sendMessage(message) }
        verify(exactly = 1) { session2.sendMessage(message) }
    }

    @Test
    fun shouldDoNothingWhenRoomDoesNotExist() {
        val roomId = UUID.randomUUID()
        val message = TextMessage("hello")

        val session = mockk<WebSocketSession>(relaxed = true)

        chatService.broadcast(roomId, message)

        verify { session wasNot Called }
    }
}