package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
import com.blikeng.chatapp.dtos.messaging.SendMessageDTO
import com.blikeng.chatapp.dtos.websocket.ReceivedMessageDTO
import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import com.blikeng.chatapp.security.crypto.Encrypted
import com.blikeng.chatapp.services.ChatService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageImpl
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.sql.Timestamp
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class ChatServiceTests {
    // ==========================
    // Tests for ChatService. Verifies:
    // - Session registration and cleanup
    // - Room join and leave behavior
    // - Message publishing and broadcasting
    // - Fetching persisted and pending messages
    // - Encryption and decryption paths
    // - Presence updates and failure cases
    // - Gauge registration and correct metric values
    // ==========================

    @MockK lateinit var chatRepository: ChatRepository
    @MockK lateinit var roomRepository: RoomRepository
    @MockK lateinit var userRoomRepository: UserRoomRepository
    @MockK lateinit var encrypt: ChatEncrypt
    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var rabbitTemplate: RabbitTemplate
    @MockK lateinit var listOps: ListOperations<String, String>
    @MockK lateinit var presenceHandler: PresenceHandler
    @MockK (relaxed = true) lateinit var meterRegistry: MeterRegistry

    @InjectMockKs lateinit var chatService: ChatService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        every { rabbitTemplate.convertAndSend(any<String>(), any<Any>()) } just Runs
        every { redisTemplate.convertAndSend(any<String>(), any<String>()) } returns 1L

        every { redisTemplate.opsForList() } returns listOps
        every { listOps.rightPush(any<String>(), any<String>()) } returns 1L
        every { listOps.range(any<String>(), any<Long>(), any<Long>()) } returns emptyList()
    }

    // ==========================
    // Session handling
    // ==========================
    @Test
    fun shouldRemoveSessionForEveryRoom(){

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))

        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val roomId = UUID.randomUUID()
        val roomId2 = UUID.randomUUID()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)

        every { session.attributes } returns attrs
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        chatService.joinRoom(roomId, session)
        chatService.joinRoom(roomId2, session)

        assertEquals(chatService.rooms[roomId]?.first(), session)
        assertEquals(chatService.rooms[roomId2]?.first(), session)

        chatService.removeSessionFromRooms(session)

        assertNull(chatService.rooms[roomId])
        assertNull(chatService.rooms[roomId2])
    }

    @Test
    fun shouldNotRemoveRoomIfOtherUsersArePresentInRoom(){
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))

        val userId = UUID.randomUUID()
        val userId2 = UUID.randomUUID()

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)
        val attrs2: MutableMap<String, Any> = hashMapOf("userId" to userId2)

        every { session.attributes } returns attrs
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        every { session2.attributes } returns attrs2
        every { session2.isOpen } returns true
        every { session2.sendMessage(any()) } just Runs

        chatService.joinRoom(roomId, session)
        chatService.joinRoom(roomId, session2)

        chatService.removeSessionFromRooms(session)

        assertNotNull(chatService.rooms[roomId])
    }

    @Test
    fun shouldKeepPresenceWhenUserStillHasSessionInRoom() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.attributes } returns hashMapOf("userId" to userId)
        every { session2.attributes } returns hashMapOf("userId" to userId)

        chatService.rooms[roomId] = mutableSetOf(session1, session2)

        chatService.removeSessionFromRooms(session1)

        assertNotNull(chatService.rooms[roomId])
        assertTrue(chatService.rooms[roomId]?.contains(session2) == true)
    }

    // ==========================
    // Room join and leave
    // ==========================
    @Test
    fun shouldJoinRoom(){
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())
        every { session.attributes } returns attrs
        every { session.isOpen } returns true

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } just Runs

        chatService.joinRoom(roomId, session)

        assertEquals(1, sent.size)

        val json = objectMapper.readTree(sent[0].payload)
        assertEquals("JOINED", json["type"].asText())

        verify(exactly = 1) { session.sendMessage(any()) }
        assertEquals(session, chatService.rooms[roomId]?.first())
    }

    @Test
    fun shouldJoinRoomWithoutSendingMessagesWhenSessionIsClosed() {
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { session.isOpen } returns false
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(
            RoomEntity(id = roomId, name = "r", type = RoomType.GROUP)
        )

        chatService.joinRoom(roomId, session)

        assertTrue(chatService.rooms[roomId]?.contains(session) == true)
        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldSkipClosedExistingSessionsWhenSendingJoinPresence() {
        val roomId = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()

        val existingSession = mockk<WebSocketSession>(relaxed = true)
        val joiningSession = mockk<WebSocketSession>(relaxed = true)

        every { existingSession.attributes } returns hashMapOf("userId" to userId1)
        every { joiningSession.attributes } returns hashMapOf("userId" to userId2)

        every { existingSession.isOpen } returns false
        every { joiningSession.isOpen } returns true

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(roomId) } returns Optional.of(
            RoomEntity(id = roomId, name = "r", type = RoomType.GROUP)
        )

        chatService.joinRoom(roomId, existingSession)
        clearMocks(existingSession)
        chatService.joinRoom(roomId, joiningSession)

        verify(exactly = 0) { existingSession.sendMessage(any()) }
    }

    @Test
    fun shouldFailToJoinRoomIfNoUserIdInSessionAttributes(){
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns emptyMap()

        val exception = assertFailsWith<ApiException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToJoinRoomIfNotAMember(){
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val session = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())
        every { session.attributes } returns attrs

        val exception = assertFailsWith<ApiException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, exception.message)
    }

    @Test
    fun shouldFailToJoinRoomIfMemberButRoomIsInvalid(){
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { roomRepository.findById(any()) } returns Optional.empty()

        val session = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())
        every { session.attributes } returns attrs

        val exception = assertFailsWith<ApiException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, exception.message)
    }

    @Test
    fun shouldLeaveRoom(){
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)

        every { session.attributes } returns attrs
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        chatService.joinRoom(roomId, session)
        assertEquals(session, chatService.rooms[roomId]?.first())

        chatService.leaveRoom(roomId, session)
        assertNull(chatService.rooms[roomId])
    }

    @Test
    fun shouldSkipClosedRemainingSessionsWhenLeavingRoom() {
        val roomId = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()

        val leavingSession = mockk<WebSocketSession>(relaxed = true)
        val remainingSession = mockk<WebSocketSession>(relaxed = true)

        every { leavingSession.attributes } returns hashMapOf("userId" to userId1)
        every { remainingSession.attributes } returns hashMapOf("userId" to userId2)
        every { remainingSession.isOpen } returns false
        every { presenceHandler.isUserOnline(userId1) } returns false

        chatService.rooms[roomId] = CopyOnWriteArraySet(mutableSetOf(leavingSession, remainingSession))

        chatService.leaveRoom(roomId, leavingSession)

        verify(exactly = 0) { remainingSession.sendMessage(any()) }
    }

    @Test
    fun shouldSkipClosedSessionsWhenNotifyingRoomPresence() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>(relaxed = true)

        every { session.isOpen } returns false

        chatService.rooms[roomId] = CopyOnWriteArraySet(mutableSetOf(session))

        chatService.notifyRoomPresence(listOf(roomId), userId, true)

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldDoNothingWhenLeavingNonExistingRoom(){
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session.attributes } returns attrs

        assertNull(chatService.rooms[roomId])
        chatService.leaveRoom(roomId, session)
    }

    @Test
    fun shouldLeaveRoomEvenWhenUserIdIsNotPresentInSessionAttributes(){
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to "")

        every { session.attributes } returns attrs

        chatService.leaveRoom(roomId, session)
        assertNull(chatService.rooms[roomId])
    }

    @Test
    fun shouldLeaveRoomOnlyFromOneSessionIfMultipleSessionsExist() {
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(
            RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP)
        )
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)

        every { session1.attributes } returns attrs
        every { session2.attributes } returns attrs
        every { session1.isOpen } returns true
        every { session2.isOpen } returns true
        every { session1.sendMessage(any()) } just Runs
        every { session2.sendMessage(any()) } just Runs

        chatService.joinRoom(roomId, session1)
        chatService.joinRoom(roomId, session2)

        assertNotNull(chatService.rooms[roomId])
        assertEquals(2, chatService.rooms[roomId]?.size)
        assertTrue(chatService.rooms[roomId]?.contains(session1) == true)
        assertTrue(chatService.rooms[roomId]?.contains(session2) == true)

        chatService.leaveRoom(roomId, session1)

        assertNotNull(chatService.rooms[roomId])
        assertEquals(1, chatService.rooms[roomId]?.size)
        assertFalse(chatService.rooms[roomId]?.contains(session1) == true)
        assertTrue(chatService.rooms[roomId]?.contains(session2) == true)
    }

    @Test
    fun shouldRemoveUserPresenceWhenRemainingRoomSessionHasInvalidUserIdType() {
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val leavingSession = mockk<WebSocketSession>()
        val invalidSession = mockk<WebSocketSession>()

        every { leavingSession.attributes } returns hashMapOf("userId" to userId)
        every { invalidSession.attributes } returns hashMapOf("userId" to "not-a-uuid")
        every { leavingSession.sendMessage(any()) } just Runs
        every { invalidSession.isOpen } returns true
        every { invalidSession.sendMessage(any()) } just Runs

        chatService.rooms[roomId] =
            CopyOnWriteArraySet(mutableSetOf(leavingSession, invalidSession))

        chatService.leaveRoom(roomId, leavingSession)

        assertNotNull(chatService.rooms[roomId])
        assertEquals(1, chatService.rooms[roomId]?.size)
        assertTrue(chatService.rooms[roomId]?.contains(invalidSession) == true)
    }

    // ==========================
    // Message publishing and broadcasting
    // ==========================
    @Test
    fun shouldFailToSendMessageIfNotAMember(){
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val message = ReceivedMessageDTO(UUID.randomUUID(), UUID.randomUUID(), "hello", "MESSAGE")

        val ex = assertFailsWith<ApiException> {
            chatService.broadcast(UUID.randomUUID(), message, "u")
        }

        assertEquals(HttpStatus.NOT_FOUND, ex.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, ex.message)
    }

    @Test
    fun shouldBroadcastMessageToAllSessionsInRoom() {

        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val message = ReceivedMessageDTO(roomId, userId, "hello", "MESSAGE")

        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)

        val attrs1: MutableMap<String, Any> = hashMapOf("userId" to userId)
        val attrs2: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session1.attributes } returns attrs1
        every { session2.attributes } returns attrs2

        chatService.joinRoom(roomId, session1)
        chatService.joinRoom(roomId, session2)

        chatService.broadcast(roomId, message, "u")

        verify(exactly = 1) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 1) { listOps.rightPush("chat.peek.${roomId}", any<String>()) }
        verify(exactly = 1) { redisTemplate.convertAndSend("room:${roomId}", any<String>()) }
    }

    @Test
    fun shouldNotPublishMessageIfNoMessageType() {
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val roomId = UUID.randomUUID()
        val message = ReceivedMessageDTO(roomId, UUID.randomUUID(), "join", "JOIN")

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session.attributes } returns attrs

        chatService.joinRoom(roomId, session)

        chatService.broadcast(roomId, message, "u")

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldPublishEncryptedMessage() {
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val nonce = "nonce".toByteArray()
        val ciphertext = "ciphertext".toByteArray()

        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { encrypt.encrypt(plaintext = "secret", aad = any()) } returns Encrypted(ciphertext, nonce)

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)
        every { session.attributes } returns attrs

        chatService.joinRoom(room.id, session)

        chatService.broadcast(room.id, ReceivedMessageDTO(room.id, user.id, "secret", "MESSAGE"), "u")

        verify(exactly = 1) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 1) { listOps.rightPush("chat.peek.${room.id}", any<String>()) }
        verify(exactly = 1) { redisTemplate.convertAndSend("room:${room.id}", any<String>()) }

        verify(exactly = 1) {
            encrypt.encrypt(
                plaintext = "secret",
                aad = any(),
            )
        }
    }

    @Test
    fun shouldNotSendFetchedMessagesWhenSessionIsClosed() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val message = SendMessageDTO(
            id = UUID.randomUUID(),
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = "Hello",
            ciphertext = null,
            nonce = null,
            timestamp = Timestamp(System.currentTimeMillis()),
            keyVersion = null
        )

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns false

        chatService.fetchAllMessages(listOf(message), session)

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldFailToPublishBlankMessage() {
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = false, type = RoomType.GROUP)

        every { roomRepository.findById(room.id) } returns Optional.of(room)

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)
        every { session.attributes } returns attrs

        chatService.joinRoom(room.id, session)

        val exception = assertFailsWith<ApiException> {
            chatService.broadcast(room.id, ReceivedMessageDTO(room.id, user.id, "         ", "MESSAGE"), "u")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)

        verify(exactly = 0) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 0) { listOps.rightPush("chat.peek.${room.id}", any<String>()) }
        verify(exactly = 0) { redisTemplate.convertAndSend("room:${room.id}", any<String>()) }
    }

    @Test
    fun shouldFailToPublishTooLongMessage() {
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true

        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = false, type = RoomType.GROUP)

        every { roomRepository.findById(room.id) } returns Optional.of(room)

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)
        every { session.attributes } returns attrs

        chatService.joinRoom(room.id, session)

        val message = "a".repeat(10000)

        val exception = assertFailsWith<ApiException> {
            chatService.broadcast(room.id, ReceivedMessageDTO(room.id, user.id, message, "MESSAGE"), "u")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)

        verify(exactly = 0) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 0) { listOps.rightPush("chat.peek.${room.id}", any<String>()) }
        verify(exactly = 0) { redisTemplate.convertAndSend("room:${room.id}", any<String>()) }
    }

    @Test
    fun shouldNotSendMessageIfRoomNotInInstanceMemory() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId) } returns true
        every { roomRepository.findById(roomId) } returns Optional.of(RoomEntity(id=roomId,name="r", type = RoomType.GROUP))
        every { chatRepository.getAllChatsByRoomId(roomId) } returns listOf()

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to userId)

        chatService.joinRoom(roomId, session)

        chatService.rooms.remove(roomId)

        val message = ReceivedMessageDTO(roomId,userId,"hello","MESSAGE")

        clearMocks(session)

        chatService.broadcast(roomId,message,"u")

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldThrowWhenEncryptedMessageHasNullNonce() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val message = SendMessageDTO(
            id = UUID.randomUUID(),
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = null,
            ciphertext = "cipher".toByteArray(),
            nonce = null,
            timestamp = Timestamp(System.currentTimeMillis()),
            keyVersion = 1
        )

        val session = mockk<WebSocketSession>(relaxed = true)

        assertFailsWith<ApiException> {
            chatService.fetchAllMessages(listOf(message), session)
        }
    }

    // ==========================
    // Message fetching and history
    // ==========================
    @Test
    fun shouldGetPersistedRoomMessagesPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat1 = ChatEntity(
            roomId = room.id,
            user = user,
            message = "Hello",
            timestamp = Timestamp(System.currentTimeMillis() - 1000)
        )
        val chat2 = ChatEntity(
            roomId = room.id,
            user = user,
            message = "Hello again",
            timestamp = Timestamp(System.currentTimeMillis())
        )

        every {
            chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any())
        } returns PageImpl(listOf(chat2, chat1))

        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns emptyList()

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(2, result.size)
        assertEquals("Hello", result[0].message)
        assertEquals("Hello again", result[1].message)
    }

    @Test
    fun shouldGetOnlyPersistedMessagesAfterPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat = ChatEntity(
            roomId = room.id,
            user = user,
            message = "Older message",
            timestamp = Timestamp(System.currentTimeMillis())
        )

        every {
            chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any())
        } returns PageImpl(listOf(chat))

        val result = chatService.getRoomMessages(room.id, 1, 25)

        assertEquals(1, result.size)
        assertEquals("Older message", result[0].message)

        verify(exactly = 0) { listOps.range(any(), any(), any()) }
    }

    @Test
    fun shouldIncludePendingRedisMessagesOnPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(
            id = UUID.randomUUID(),
            username = "u",
            password = ""
        )

        every {
            chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any())
        } returns PageImpl(emptyList())

        val pending = RabbitMessageDTO(
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = "from redis"
        )

        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns
                listOf(objectMapper.writeValueAsString(pending))

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(1, result.size)
        assertEquals("from redis", result[0].message)
    }

    @Test
    fun shouldMergePersistedAndPendingMessagesOnPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(
            id = UUID.randomUUID(),
            username = "u",
            password = ""
        )

        val persisted = ChatEntity(
            roomId = room.id,
            user = user,
            message = "persisted",
            timestamp = Timestamp(System.currentTimeMillis() - 1000)
        )

        every {
            chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any())
        } returns PageImpl(listOf(persisted))

        val pending = RabbitMessageDTO(
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = "pending"
        )

        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns
                listOf(objectMapper.writeValueAsString(pending))

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(2, result.size)
        assertTrue(result.any { it.message == "persisted" })
        assertTrue(result.any { it.message == "pending" })
    }

    @Test
    fun shouldReturnEncryptedMessagesAsEncryptedPayloadInHistory() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat = ChatEntity(
            roomId = room.id,
            user = user,
            message = null,
            ciphertext = "cipher".toByteArray(),
            nonce = "nonce".toByteArray(),
            keyVersion = 1,
            timestamp = Timestamp(System.currentTimeMillis())
        )

        every {
            chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any())
        } returns PageImpl(listOf(chat))

        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns emptyList()

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(1, result.size)
        assertNull(result[0].message)
        assertArrayEquals("cipher".toByteArray(), result[0].ciphertext)
        assertArrayEquals("nonce".toByteArray(), result[0].nonce)
        assertEquals(1, result[0].keyVersion)
    }

    @Test
    fun shouldDecryptEncryptedMessageWhenFetchingAllMessages() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val ciphertext = "cipher".toByteArray()
        val nonce = "nonce".toByteArray()

        val message = SendMessageDTO(
            id = UUID.randomUUID(),
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = null,
            keyVersion = 1,
            ciphertext = ciphertext,
            nonce = nonce,
            timestamp = Timestamp(System.currentTimeMillis()),
        )

        every {
            encrypt.decrypt(
                ciphertext = ciphertext,
                nonce = nonce,
                aad = any(),
            )
        } returns "decrypted message"

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        chatService.fetchAllMessages(listOf(message), session)

        verify(exactly = 1) {
            encrypt.decrypt(
                ciphertext = ciphertext,
                nonce = nonce,
                aad = any(),
            )
        }

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains("\"content\":\"decrypted message\""))
    }

    @Test
    fun shouldSendEmptyStringWhenCiphertextNullAndMessageNull() {
        val room = RoomEntity(name = "r", encrypted = false, keyVersion = null, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat = SendMessageDTO(
            id = UUID.randomUUID(),
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = null,
            keyVersion = null,
            ciphertext = null,
            nonce = null,
            timestamp = Timestamp(System.currentTimeMillis()),
        )

        val session = mockk<WebSocketSession>(relaxed = true)
        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs
        every { session.isOpen } returns true

        chatService.fetchAllMessages(listOf(chat), session)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains("\"content\":\"\""))
    }

    @Test
    fun shouldSendPlainMessageWhenCiphertextIsNullAndMessageExists() {
        val room = RoomEntity(name = "r", encrypted = false, keyVersion = null, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat = SendMessageDTO(
            id = UUID.randomUUID(),
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = "Hello",
            keyVersion = null,
            ciphertext = null,
            nonce = null,
            timestamp = Timestamp(System.currentTimeMillis()),
        )

        val session = mockk<WebSocketSession>(relaxed = true)
        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs
        every { session.isOpen } returns true

        chatService.fetchAllMessages(listOf(chat), session)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains("\"content\":\"Hello\""))
    }

    @Test
    fun shouldFailToGetMessagesWhenUsingInvalidParameters(){
        val pageNumberException = assertFailsWith<ApiException> {
            chatService.getRoomMessages(UUID.randomUUID(), -1, 25)
        }

        assertEquals(HttpStatus.BAD_REQUEST, pageNumberException.status)
        assertEquals(ErrorMessages.INVALID_PARAMETERS, pageNumberException.message)


        val pageSizeException = assertFailsWith<ApiException> {
            chatService.getRoomMessages(UUID.randomUUID(), 0, 250)
        }

        assertEquals(HttpStatus.BAD_REQUEST, pageSizeException.status)
        assertEquals(ErrorMessages.INVALID_PARAMETERS, pageSizeException.message)
    }

    // ==========================
    // Redis pending message handling
    // ==========================
    @Test
    fun shouldReturnEmptyListWhenRedisRangeReturnsNull() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)

        every {
            chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any())
        } returns PageImpl(emptyList())

        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns null

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertTrue(result.isEmpty())
    }

    // ==========================
    // Meter registry
    // ==========================
    @Test
    fun shouldRegisterAndEvaluateChatGauges() {
        val meterRegistry = SimpleMeterRegistry()

        val chatService = ChatService(
            chatRepository = chatRepository,
            roomRepository = roomRepository,
            userRoomRepository = userRoomRepository,
            encrypt = encrypt,
            redisTemplate = redisTemplate,
            rabbitTemplate = rabbitTemplate,
            objectMapper = objectMapper,
            presenceHandler = presenceHandler,
            meterRegistry = meterRegistry,
        )

        val roomsGauge = meterRegistry.get("chat.rooms").gauge()

        assertEquals(0.0, roomsGauge.value())

        val room1 = UUID.randomUUID()
        val room2 = UUID.randomUUID()

        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        chatService.rooms[room1] = CopyOnWriteArraySet(mutableSetOf(session1))
        chatService.rooms[room2] = CopyOnWriteArraySet(mutableSetOf(session2))

        assertEquals(2.0, roomsGauge.value())
    }
}