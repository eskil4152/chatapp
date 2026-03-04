package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.ChatEncrypt
import com.blikeng.chatapp.security.Encrypted
import com.blikeng.chatapp.services.ChatFlushService
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.ReceivedMessage
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class ChatServiceTests {
    @MockK lateinit var chatRepository: ChatRepository
    @MockK lateinit var roomRepository: RoomRepository
    @MockK lateinit var userRepository: UserRepository
    @MockK lateinit var userRoomRepository: UserRoomRepository
    @MockK lateinit var encrypt: ChatEncrypt
    @MockK lateinit var chatFlushService: ChatFlushService

    @InjectMockKs lateinit var chatService: ChatService

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
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val roomId = UUID.randomUUID()
        val roomId2 = UUID.randomUUID()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)

        every { session.attributes } returns attrs
        every { session.sendMessage(any()) } just Runs

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
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())
        every { session.attributes } returns attrs

        chatService.joinRoom(roomId, session)

        assertEquals(session, chatService.rooms[roomId]?.first())
    }

    @Test
    fun shouldFailToJoinRoomIfNoUsernameInSessionAttributes(){
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns emptyMap()

        val exception = assertFailsWith<ResponseStatusException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToJoinRoomIfNotAMember(){
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val session = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())
        every { session.attributes } returns attrs

        val exception = assertFailsWith<ResponseStatusException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.FORBIDDEN, exception.statusCode)
        assertEquals("Not permitted", exception.reason)
    }

    @Test
    fun shouldLeaveRoom(){
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session.attributes } returns attrs

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
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val roomId = UUID.randomUUID()
        val message = ReceivedMessage(roomId, UUID.randomUUID(), "hello", "MESSAGE")

        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session1.attributes } returns attrs
        every { session2.attributes } returns attrs

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

        chatService.broadcast(UUID.randomUUID(), message, "u")

        verify(exactly = 0) { chatFlushService.saveBatch(any()) }
    }

    @Test
    fun shouldNotSaveMessageIfNotMessageType() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r"))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val roomId = UUID.randomUUID()
        val message = ReceivedMessage(roomId, UUID.randomUUID(), "join", "JOIN")

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session.attributes } returns attrs

        chatService.joinRoom(roomId, session)

        chatService.broadcast(roomId, message, "u")

        verify(exactly = 1) { session.sendMessage(any()) }
        verify(exactly = 0) { chatFlushService.saveBatch(any()) }
    }

    @Test
    fun shouldFetchAllSavedMessages() {
        val room = RoomEntity(name = "r")
        val user = UserEntity(username = "u", password = "")

        val chat1 = ChatEntity(room = room, user = user, message =  "Hello", timestamp =  Timestamp(System.currentTimeMillis()))
        val chat2 = ChatEntity(room = room, user = user, message =  "Hello again", timestamp =  Timestamp(System.currentTimeMillis()))
        val saved: List<ChatEntity> = listOf(chat1, chat2)

        every { chatRepository.getAllChatsByRoomId(any()) } returns saved
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)
        val session = mockk<WebSocketSession>(relaxed = true)

        every { session.attributes } returns attrs

        chatService.joinRoom(room.id, session)

        verify(exactly = 2) { session.sendMessage(any())}
        verify(exactly = 1) { chatRepository.getAllChatsByRoomId(room.id) }
        verify(exactly = 0) { chatFlushService.saveBatch(any()) }
    }

    @Test
    fun shouldFetchAllSavedEncryptedMessages() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1)
        val user = UserEntity(username = "u", password = "")

        val chat = ChatEntity(
            room = room,
            user = user,
            message = null,
            ciphertext = "cipher".toByteArray(),
            nonce = "nonce".toByteArray(),
            keyVersion = 1,
            timestamp = Timestamp(System.currentTimeMillis())
        )

        val saved: List<ChatEntity> = listOf(chat)

        every { chatRepository.getAllChatsByRoomId(any()) } returns saved
        every { encrypt.decrypt(any(), any(), any(), any()) } returns "message"
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)

        every { session.attributes } returns attrs

        chatService.joinRoom(room.id, session)

        verify(exactly = 1) { session.sendMessage(any()) }
        verify(exactly = 1) { encrypt.decrypt(any(), any(), any(), any()) }
        verify(exactly = 1) { chatRepository.getAllChatsByRoomId(room.id) }
        verify(exactly = 0) { chatFlushService.saveBatch(any()) }
    }

    @Test
    fun shouldBufferAndFlushUnencryptedMessage() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()

        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = false, keyVersion = null)

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user.id, room.id) } returns true

        val batchSlot = slot<List<ChatEntity>>()
        every { chatFlushService.saveBatch(capture(batchSlot)) } just Runs

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)

        val session = mockk<WebSocketSession>()
        every { session.attributes } returns attrs
        every { session.sendMessage(any()) } just Runs

        chatService.joinRoom(room.id, session)

        chatService.broadcast(room.id, ReceivedMessage(room.id, user.id, "hello", "MESSAGE"), "u")

        chatService.scheduledFlush()

        verify(exactly = 1) { chatFlushService.saveBatch(any()) }

        val e = batchSlot.captured.single()
        assertEquals("hello", e.message)
        assertNull(e.ciphertext)
        assertNull(e.nonce)
        assertNull(e.keyVersion)
    }

    @Test
    fun shouldBufferAndFlushEncryptedMessage() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val nonce = "nonce".toByteArray()
        val ciphertext = "ciphertext".toByteArray()

        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1)

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roomRepository.findById(room.id) } returns Optional.of(room)

        every { encrypt.encrypt(plaintext = "secret", aad = any(), keyVersion = 1) } returns Encrypted(ciphertext, nonce)

        val batchSlot = slot<List<ChatEntity>>()
        every { chatFlushService.saveBatch(capture(batchSlot)) } just Runs

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)

        every { session.attributes } returns attrs

        chatService.joinRoom(room.id, session)

        chatService.broadcast(room.id, ReceivedMessage(room.id, user.id, "secret", "MESSAGE"), "u")

        chatService.scheduledFlush()

        val e = batchSlot.captured.single()
        assertNull(e.message)
        assertEquals(ciphertext, e.ciphertext)
        assertEquals(nonce, e.nonce)
        assertEquals(1, e.keyVersion)
    }

    @Test
    fun shouldFlushBeforeShutdown() {
        every { chatFlushService.saveBatch(any()) } just Runs
    }

    @Test
    fun shouldReturnEarlyIfAlreadyFlushing() {
        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = false, keyVersion = null)

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roomRepository.findById(room.id) } returns Optional.of(room)

        chatService.addMessage(ReceivedMessage(room.id, user.id, "hello", "MESSAGE"))

        val enteredSaveBatch = CountDownLatch(1)
        val releaseSaveBatch = CountDownLatch(1)

        every { chatFlushService.saveBatch(any()) } answers {
            enteredSaveBatch.countDown()
            releaseSaveBatch.await(2, TimeUnit.SECONDS)
        }

        val exec = Executors.newSingleThreadExecutor()
        val f1 = exec.submit { chatService.scheduledFlush() }

        assertTrue(enteredSaveBatch.await(2, TimeUnit.SECONDS))

        chatService.scheduledFlush()

        releaseSaveBatch.countDown()
        f1.get(2, TimeUnit.SECONDS)
        exec.shutdownNow()

        verify(exactly = 1) { chatFlushService.saveBatch(any()) }
    }

    @Test
    fun shouldOnlySendBufferedMessagesForJoinedRoom() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val room1 = RoomEntity(name = "r1", encrypted = false, keyVersion = null)
        val room2 = RoomEntity(name = "r2", encrypted = false, keyVersion = null)

        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { userRepository.findById(user.id) } returns Optional.of(user)
        every { roomRepository.findById(room1.id) } returns Optional.of(room1)
        every { roomRepository.findById(room2.id) } returns Optional.of(room2)

        chatService.addMessage(ReceivedMessage(room1.id, user.id, "one", "MESSAGE"))
        chatService.addMessage(ReceivedMessage(room2.id, user.id, "two", "MESSAGE"))

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)

        every { session.attributes } returns attrs

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        chatService.joinRoom(room1.id, session)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains("\"content\":\"one\""))
    }

    @Test
    fun shouldSendEmptyStringWhenCiphertextNullAndMessageNull() {
        val room = RoomEntity(name = "r", encrypted = false, keyVersion = null)
        val user = UserEntity(username = "u", password = "")

        val chat = ChatEntity(
            room = room,
            user = user,
            message = null,
            timestamp = Timestamp(System.currentTimeMillis())
        )
        chat.ciphertext = null
        chat.nonce = null
        chat.keyVersion = null

        val session = mockk<WebSocketSession>(relaxed = true)
        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        chatService.fetchAllMessages(listOf(chat), session)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains("\"content\":\"\""))
    }

    @Test
    fun shouldFlushOnShutdown() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val room = RoomEntity(id = roomId, name = "r", encrypted = false, keyVersion = null)
        val user = UserEntity(id = userId, username = "u", password = "")

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { roomRepository.findById(roomId) } returns Optional.of(room)
        every { chatFlushService.saveBatch(any()) } returns Unit

        chatService.addMessage(ReceivedMessage(roomId, userId, "hello", "MESSAGE"))

        chatService.shutdownFlush()

        verify(exactly = 1) { chatFlushService.saveBatch(match { it.size == 1 }) }
    }
}