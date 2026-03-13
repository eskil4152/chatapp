package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.dtos.RabbitMessageDTO
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.ChatEncrypt
import com.blikeng.chatapp.security.Encrypted
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.dtos.ReceivedMessageDTO
import com.blikeng.chatapp.dtos.SendMessageDTO
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.sql.Timestamp
import java.util.*
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class ChatServiceTests {
    @MockK lateinit var chatRepository: ChatRepository
    @MockK lateinit var roomRepository: RoomRepository
    @MockK lateinit var userRepository: UserRepository
    @MockK lateinit var userRoomRepository: UserRoomRepository
    @MockK lateinit var encrypt: ChatEncrypt
    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var rabbitTemplate: RabbitTemplate
    @MockK lateinit var listOps: ListOperations<String, String>
    @MockK lateinit var presenceHandler: PresenceHandler

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

    @Test
    fun shouldRegisterSession(){
        every { presenceHandler.userConnected(any()) } just Runs

        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.registerSession(userId, session)

        assertEquals(setOf(session), chatService.users[userId])
    }

    @Test
    fun shouldRemoveSession(){
        every { presenceHandler.userConnected(any()) } just Runs
        every { presenceHandler.userDisconnected(any()) } just Runs

        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        chatService.registerSession(userId, session)

        assertEquals(setOf(session), chatService.users[userId])
        chatService.removeSession(userId, session)
        assertNull(chatService.users[userId])
    }

    @Test
    fun shouldRemoveSessionForEveryRoom(){
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { presenceHandler.userConnected(any()) } just Runs
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs
        every { presenceHandler.userLeftRoom(any(), any()) } just Runs
        every { presenceHandler.userDisconnected(any()) } just Runs

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

        assertEquals(setOf(session), chatService.users[userId])
        assertEquals(chatService.rooms[roomId]?.first(), session)
        assertEquals(chatService.rooms[roomId2]?.first(), session)

        chatService.removeSession(userId, session)

        assertNull(chatService.users[userId])
        assertNull(chatService.rooms[roomId])
        assertNull(chatService.rooms[roomId2])
    }

    @Test
    fun shouldJoinRoom(){
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())
        every { session.attributes } returns attrs
        every { session.sendMessage(any()) } just Runs

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
    fun shouldLeaveRoom(){
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs
        every { presenceHandler.userLeftRoom(any(), any()) } just Runs

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)

        every { session.attributes } returns attrs
        every { session.sendMessage(any()) } just Runs

        chatService.joinRoom(roomId, session)
        assertEquals(session, chatService.rooms[roomId]?.first())

        chatService.leaveRoom(roomId, session)
        assertNull(chatService.rooms[roomId]?.isEmpty())
    }

    @Test
    fun shouldDoNothingWhenLeavingNonExistingRoom(){
        every { presenceHandler.userLeftRoom(any(), any()) } just Runs

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session.attributes } returns attrs

        assertNull(chatService.rooms[roomId])
        chatService.leaveRoom(roomId, session)
    }

    @Test
    fun shouldBroadcastMessageToAllSessionsInRoom() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

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
    fun shouldNotPublishMessageIfNotMessageType() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val roomId = UUID.randomUUID()
        val message = ReceivedMessageDTO(roomId, UUID.randomUUID(), "join", "JOIN")

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to UUID.randomUUID())

        every { session.attributes } returns attrs

        chatService.joinRoom(roomId, session)

        chatService.broadcast(roomId, message, "u")

        verify(exactly = 1) { session.sendMessage(any()) }
    }

    @Test
    fun shouldFetchAllSavedMessages() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat1 = ChatEntity(roomId = room.id, user = user, message =  "Hello", timestamp = Timestamp(System.currentTimeMillis()))
        val chat2 = ChatEntity(roomId = room.id, user = user, message =  "Hello again", timestamp = Timestamp(System.currentTimeMillis()))
        val saved: List<ChatEntity> = listOf(chat1, chat2)

        every { chatRepository.getAllChatsByRoomId(any()) } returns saved
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { roomRepository.findById(any()) } returns Optional.of(room)
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)
        val session = mockk<WebSocketSession>(relaxed = true)

        every { session.attributes } returns attrs

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } just Runs

        chatService.joinRoom(room.id, session)

        assertEquals(3, sent.size)

        val mapper = objectMapper
        val types = sent.map { mapper.readTree(it.payload)["type"].asText() }

        assertEquals(listOf("JOINED","MESSAGE","MESSAGE"), types)

        verify(exactly = 3) { session.sendMessage(any())}
        verify(exactly = 1) { chatRepository.getAllChatsByRoomId(room.id) }
    }

    @Test
    fun shouldFetchAllSavedEncryptedMessages() {
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

        val saved: List<ChatEntity> = listOf(chat)

        every { chatRepository.getAllChatsByRoomId(any()) } returns saved
        every { encrypt.decrypt(any(), any(), any(), any()) } returns "message"
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { roomRepository.findById(any()) } returns Optional.of(room)
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val session = mockk<WebSocketSession>(relaxed = true)
        val attrs: MutableMap<String, Any> = hashMapOf("userId" to user.id)

        every { session.attributes } returns attrs

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } returns Unit

        chatService.joinRoom(room.id, session)

        assertEquals(2, sent.size)

        val mapper = objectMapper
        val types = sent.map { mapper.readTree(it.payload)["type"].asText() }

        assertEquals("JOINED", types[0])
        assertEquals(listOf("MESSAGE"), types.drop(1))

        verify(exactly = 1) { chatRepository.getAllChatsByRoomId(room.id) }
        verify(exactly = 1) {
            encrypt.decrypt(
                ciphertext = chat.ciphertext!!,
                nonce = chat.nonce!!,
                aad = any(),
                keyVersion = 1
            )
        }
    }

    @Test
    fun shouldPublishEncryptedMessage() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val nonce = "nonce".toByteArray()
        val ciphertext = "ciphertext".toByteArray()

        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { encrypt.encrypt(plaintext = "secret", aad = any(), keyVersion = 1) } returns Encrypted(ciphertext, nonce)

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
                keyVersion = 1
            )
        }
    }

    @Test
    fun shouldOnlySendPendingRedisMessagesForJoinedRoom() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val room1 = RoomEntity(name = "r1", encrypted = false, keyVersion = null, type = RoomType.GROUP)
        val room2 = RoomEntity(name = "r2", encrypted = false, keyVersion = null, type = RoomType.GROUP)

        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { roomRepository.findById(room1.id) } returns Optional.of(room1)
        every { roomRepository.findById(room2.id) } returns Optional.of(room2)

        val msg1Json = objectMapper.writeValueAsString(
            RabbitMessageDTO(
                roomId = room1.id,
                userId = user.id,
                username = user.username,
                message = "one"
            )
        )

        val msg2Json = objectMapper.writeValueAsString(
            RabbitMessageDTO(
                roomId = room2.id,
                userId = user.id,
                username = user.username,
                message = "two"
            )
        )

        every { listOps.range("chat.peek.${room1.id}", 0L, -1L) } returns listOf(msg1Json)
        every { listOps.range("chat.peek.${room2.id}", 0L, -1L) } returns listOf(msg2Json)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } just Runs

        chatService.joinRoom(room1.id, session)

        verify(exactly = 2) { session.sendMessage(any()) }
        assertEquals(2, sent.size)

        val types = sent.map { objectMapper.readTree(it.payload)["type"].asText() }
        assertEquals(listOf("JOINED", "MESSAGE"), types)

        assertTrue(sent.any { it.payload.contains("\"content\":\"one\"") })
        assertFalse(sent.any { it.payload.contains("\"content\":\"two\"") })
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

        chatService.fetchAllMessages(listOf(chat), session)

        verify(exactly = 1) { session.sendMessage(any()) }
        assertTrue(msgSlot.captured.payload.contains("\"content\":\"\""))
    }

    @Test
    fun shouldNotSendMessageIfRoomNotInMemory() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId) } returns true
        every { userRepository.findById(userId) } returns Optional.of(UserEntity(username="u",password=""))
        every { roomRepository.findById(roomId) } returns Optional.of(RoomEntity(id=roomId,name="r", type = RoomType.GROUP))
        every { chatRepository.getAllChatsByRoomId(roomId) } returns listOf()
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

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
    fun shouldKeepUserWhenRemovingOneOfMultipleSessions() {
        every { presenceHandler.userConnected(any()) } just Runs
        every { presenceHandler.userDisconnected(any()) } just Runs

        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        chatService.registerSession(userId, session1)
        chatService.registerSession(userId, session2)

        chatService.removeSession(userId, session1)

        assertNotNull(chatService.users[userId])
        assertEquals(1, chatService.users[userId]?.size)
        assertTrue(chatService.users[userId]?.contains(session2) == true)
    }

    @Test
    fun shouldKeepUserWhenRemovingSessionThatWasNotPresent() {
        every { presenceHandler.userConnected(any()) } just Runs
        every { presenceHandler.userDisconnected(any()) } just Runs

        val userId = UUID.randomUUID()
        val existingSession = mockk<WebSocketSession>()
        val otherSession = mockk<WebSocketSession>()

        chatService.registerSession(userId, existingSession)

        chatService.removeSession(userId, otherSession)

        assertNotNull(chatService.users[userId])
        assertEquals(1, chatService.users[userId]?.size)
        assertTrue(chatService.users[userId]?.contains(existingSession) == true)
    }

    @Test
    fun shouldReadPendingMessagesFromRedisOnJoin() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val room = RoomEntity(name = "r", encrypted = false, keyVersion = null, type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { roomRepository.findById(room.id) } returns Optional.of(room)

        val pending = RabbitMessageDTO(
            roomId = room.id,
            userId = user.id,
            username = user.username,
            message = "from redis"
        )

        val pendingJson = objectMapper.writeValueAsString(pending)
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns listOf(pendingJson)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } just Runs

        chatService.joinRoom(room.id, session)

        assertEquals(2, sent.size)

        val types = sent.map { objectMapper.readTree(it.payload)["type"].asText() }
        assertEquals(listOf("JOINED", "MESSAGE"), types)
        assertTrue(sent.any { it.payload.contains("\"content\":\"from redis\"") })
    }

    @Test
    fun shouldReturnNoPendingMessagesWhenRedisRangeIsNull() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val room = RoomEntity(name = "r", encrypted = false, type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns null

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } just Runs

        chatService.joinRoom(room.id, session)

        assertEquals(1, sent.size)
        assertEquals("JOINED", objectMapper.readTree(sent[0].payload)["type"].asText())
    }

    @Test
    fun shouldReturnNoPendingMessagesWhenRedisRangeIsEmpty() {
        every { chatRepository.getAllChatsByRoomId(any()) } returns emptyList()
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { presenceHandler.userJoinedRoom(any(), any()) } just Runs

        val room = RoomEntity(name = "r", encrypted = false, type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns emptyList()

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        val sent = mutableListOf<TextMessage>()
        every { session.sendMessage(capture(sent)) } just Runs

        chatService.joinRoom(room.id, session)

        assertEquals(1, sent.size)
        assertEquals("JOINED", objectMapper.readTree(sent[0].payload)["type"].asText())
    }

    @Test
    fun shouldDoNothingWhenRemovingSessionForUnknownUser() {
        every { presenceHandler.userDisconnected(any()) } just Runs

        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        chatService.removeSession(userId, session)

        assertNull(chatService.users[userId])
    }
}