package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.ReceivedMessage
import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.hibernate.query.restriction.Restriction.any
import org.hibernate.query.sqm.produce.function.StandardArgumentsValidators.exactly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Timestamp
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ChatServiceTests {
    @MockK
    private lateinit var chatRepository: ChatRepository

    @MockK
    private lateinit var roomRepository: RoomRepository

    @MockK
    lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var chatService: ChatService

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
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()

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
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.joinRoom(roomId, session)

        assertEquals(session, chatService.rooms[roomId]?.first())
    }

    @Test
    fun shouldLeaveRoom(){
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.joinRoom(roomId, session)
        assertEquals(session, chatService.rooms[roomId]?.first())

        chatService.leaveRoom(roomId, session)
        assertTrue { chatService.rooms[roomId]?.isEmpty() == true }
    }

    @Test
    fun shouldDoNothingWhenLeavingNonExistingRoom(){
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        assertNull(chatService.rooms[roomId])
        chatService.leaveRoom(roomId, session)
        assertTrue { chatService.rooms[roomId] == null }
    }

    @Test
    fun shouldBroadcastMessageToAllSessionsInRoom() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r"))
        every { chatRepository.save(any()) } answers { firstArg() }

        val roomId = UUID.randomUUID()
        val message = ReceivedMessage(roomId, UUID.randomUUID(), "hello", "MESSAGE")

        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)

        chatService.joinRoom(roomId, session1)
        chatService.joinRoom(roomId, session2)

        chatService.broadcast(roomId, message, "u")

        verify(exactly = 1) { session1.sendMessage(any()) }
        verify(exactly = 1) { session2.sendMessage(any()) }
    }

    @Test
    fun shouldBroadcastNothingWhenRoomDoesNotExist() {
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r"))

        val message = ReceivedMessage(UUID.randomUUID(), UUID.randomUUID(), "hello", "MESSAGE")

        val session = mockk<WebSocketSession>(relaxed = true)

        chatService.broadcast(UUID.randomUUID(), message, "u")

        verify { session wasNot Called }
    }

    @Test
    fun shouldNotSaveMessageIfNotMessageType() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r"))

        val roomId = UUID.randomUUID()
        val message = ReceivedMessage(roomId, UUID.randomUUID(), "join", "JOIN")

        val session = mockk<WebSocketSession>(relaxed = true)

        chatService.joinRoom(roomId, session)

        chatService.broadcast(roomId, message, "u")

        verify(exactly = 1) { session.sendMessage(any()) }
        verify(exactly = 0) { chatRepository.save(any()) }
    }

    @Test
    fun shouldFetchAllSavedMessages() {
        val roomId = UUID.randomUUID()
        val room = RoomEntity(roomId, "r")
        val user = UserEntity(username = "u", password = "")

        val chat1 = ChatEntity(room = room, user = user, message =  "Hello", timestamp =  Timestamp(System.currentTimeMillis()))
        val chat2 = ChatEntity(room = room, user = user, message =  "Hello again", timestamp =  Timestamp(System.currentTimeMillis()))
        val saved: List<ChatEntity> = listOf(chat1, chat2)

        every { chatRepository.getAllChatsByRoomId(any()) } returns saved

        val session = mockk<WebSocketSession>(relaxed = true)

        chatService.joinRoom(roomId, session)

        verify(exactly = 2) { session.sendMessage(any())}
        verify(exactly = 1) { chatRepository.getAllChatsByRoomId(roomId) }
        verify(exactly = 0) { chatRepository.save(any()) }
    }
}