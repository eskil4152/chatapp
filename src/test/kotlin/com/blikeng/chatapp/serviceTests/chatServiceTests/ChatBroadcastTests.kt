package com.blikeng.chatapp.serviceTests.chatServiceTests

import com.blikeng.chatapp.dtos.websocket.ReceivedMessage
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import com.blikeng.chatapp.security.crypto.Encrypted
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.web.socket.WebSocketSession
import java.util.Collections.emptyList
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class ChatBroadcastTests {
    // ==========================
    // Tests for ChatService message broadcasting.
    // Verifies:
    // - Membership check before broadcast
    // - Message published to RabbitMQ and Redis
    // - Encrypted message path
    // - Blank and oversized message rejection
    // - No direct session send when room not tracked locally
    // ==========================

    @MockK lateinit var chatRepository: ChatRepository

    @MockK lateinit var roomRepository: RoomRepository

    @MockK lateinit var userRoomRepository: UserRoomRepository

    @MockK lateinit var encrypt: ChatEncrypt

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK lateinit var rabbitTemplate: RabbitTemplate

    @MockK lateinit var listOps: ListOperations<String, String>

    @MockK lateinit var valueOps: ValueOperations<String, String>

    @MockK lateinit var presenceHandler: PresenceHandler

    @MockK lateinit var userService: UserService

    @MockK(relaxed = true)
    lateinit var meterRegistry: MeterRegistry

    @InjectMockKs lateinit var chatService: ChatService
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

    @BeforeEach
    fun setup() {
        every { rabbitTemplate.convertAndSend(any<String>(), any<Any>()) } just Runs
        every { redisTemplate.convertAndSend(any<String>(), any<String>()) } returns 1L
        every { redisTemplate.opsForList() } returns listOps
        every { redisTemplate.opsForValue() } returns valueOps
        every { listOps.rightPush(any<String>(), any<String>()) } returns 1L
        every { listOps.range(any<String>(), any<Long>(), any<Long>()) } returns emptyList()
        every { valueOps.get(any<String>()) } returns null
        every { valueOps.set(any<String>(), any<String>(), any<java.time.Duration>()) } just Runs
        every { userRoomRepository.findAllIdUserIdsByIdRoomId(any()) } returns emptyList()
    }

    @Test
    fun shouldFailToSendMessageIfNotAMember() {
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns false

        val ex =
            assertThrows<ApiException> {
                chatService.broadcast(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ReceivedMessage(UUID.randomUUID(), UUID.randomUUID(), "hello", "MESSAGE"),
                    "u",
                )
            }

        assertEquals(HttpStatus.NOT_FOUND, ex.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, ex.message)
    }

    @Test
    fun shouldBroadcastMessageToAllSessionsInRoom() {
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)
        every { session1.attributes } returns hashMapOf("userId" to userId)
        every { session2.attributes } returns hashMapOf("userId" to UUID.randomUUID())

        chatService.joinRoom(roomId, session1)
        chatService.joinRoom(roomId, session2)

        chatService.broadcast(roomId, userId, ReceivedMessage(roomId, userId, "hello", "MESSAGE"), "u")

        verify(exactly = 1) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 1) { listOps.rightPush("chat.peek.$roomId", any<String>()) }
        verify(exactly = 1) { redisTemplate.convertAndSend("room:$roomId", any<String>()) }
    }

    @Test
    fun shouldNotPublishMessageIfNoMessageType() {
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to userId)

        chatService.joinRoom(roomId, session)
        chatService.broadcast(roomId, userId, ReceivedMessage(roomId, UUID.randomUUID(), "join", "JOIN"), "u")

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldPublishEncryptedMessage() {
        val nonce = "nonce".toByteArray()
        val ciphertext = "ciphertext".toByteArray()
        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { encrypt.encrypt(plaintext = "secret", aad = any()) } returns Encrypted(ciphertext, nonce)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        chatService.joinRoom(room.id, session)
        chatService.broadcast(room.id, user.id, ReceivedMessage(room.id, user.id, "secret", "MESSAGE"), "u")

        verify(exactly = 1) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 1) { listOps.rightPush("chat.peek.${room.id}", any<String>()) }
        verify(exactly = 1) { redisTemplate.convertAndSend("room:${room.id}", any<String>()) }
        verify(exactly = 1) { encrypt.encrypt(plaintext = "secret", aad = any()) }
    }

    @Test
    fun shouldFailToPublishBlankMessage() {
        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = false, type = RoomType.GROUP)

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { roomRepository.findById(room.id) } returns Optional.of(room)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        chatService.joinRoom(room.id, session)

        val exception =
            assertThrows<ApiException> {
                chatService.broadcast(room.id, user.id, ReceivedMessage(room.id, user.id, "         ", "MESSAGE"), "u")
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        verify(exactly = 0) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 0) { listOps.rightPush("chat.peek.${room.id}", any<String>()) }
        verify(exactly = 0) { redisTemplate.convertAndSend("room:${room.id}", any<String>()) }
    }

    @Test
    fun shouldFailToPublishTooLongMessage() {
        val user = UserEntity(username = "u", password = "")
        val room = RoomEntity(name = "r", encrypted = false, type = RoomType.GROUP)

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { roomRepository.findById(room.id) } returns Optional.of(room)

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to user.id)

        chatService.joinRoom(room.id, session)

        val exception =
            assertThrows<ApiException> {
                chatService.broadcast(room.id, user.id, ReceivedMessage(room.id, user.id, "a".repeat(10000), "MESSAGE"), "u")
            }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        verify(exactly = 0) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
        verify(exactly = 0) { listOps.rightPush("chat.peek.${room.id}", any<String>()) }
        verify(exactly = 0) { redisTemplate.convertAndSend("room:${room.id}", any<String>()) }
    }

    @Test
    fun shouldFailToAddMessageWhenRoomNotFoundInRepository() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId) } returns true
        every { roomRepository.findById(roomId) } returns Optional.empty()

        val exception =
            assertThrows<ApiException> {
                chatService.broadcast(roomId, userId, ReceivedMessage(roomId, userId, "hello", "MESSAGE"), "u")
            }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, exception.message)
        verify(exactly = 0) { rabbitTemplate.convertAndSend("chat.buffer", any<Any>()) }
    }

    @Test
    fun shouldDefaultRoomNameWhenNotCached() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberId = UUID.randomUUID()

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findAllIdUserIdsByIdRoomId(roomId) } returns listOf(memberId)
        every { presenceHandler.isUserOnline(memberId) } returns true

        val channels = mutableListOf<String>()
        val payloads = mutableListOf<String>()
        every { redisTemplate.convertAndSend(capture(channels), capture(payloads)) } returns 1L

        chatService.broadcast(roomId, userId, ReceivedMessage(roomId, userId, "u joined", "JOIN"), "u")

        val idx = channels.indexOfFirst { it == "user:$memberId" }
        val json = objectMapper.readTree(payloads[idx])
        assertEquals("MESSAGE_NOTIFICATION", json["type"].asText())
        assertEquals("Unknown room", json["roomName"].asText())
    }

    @Test
    fun shouldPublishTypingToRoomChannel() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val payloadSlot = slot<String>()
        every { redisTemplate.convertAndSend(eq("room:$roomId"), capture(payloadSlot)) } returns 1L

        chatService.notifyTyping(roomId, userId, "alice")

        val json = objectMapper.readTree(payloadSlot.captured)
        assertEquals("TYPING", json["type"].asText())
        assertEquals(userId.toString(), json["userId"].asText())
        assertEquals(roomId.toString(), json["roomId"].asText())
        assertEquals("alice", json["username"].asText())
    }

    @Test
    fun shouldReturnCachedRoomMemberIds() {
        val roomId = UUID.randomUUID()
        val memberId = UUID.randomUUID()

        every { valueOps.get("room:$roomId:members") } returns objectMapper.writeValueAsString(listOf(memberId))

        val result = chatService.getRoomMemberIds(roomId)

        assertEquals(listOf(memberId), result)
        verify(exactly = 0) { userRoomRepository.findAllIdUserIdsByIdRoomId(any()) }
    }

    @Test
    fun shouldNotSendNotificationToOfflineMember() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val memberId = UUID.randomUUID()

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(any(), any()) } returns true
        every { userRoomRepository.findAllIdUserIdsByIdRoomId(roomId) } returns listOf(memberId)
        every { presenceHandler.isUserOnline(memberId) } returns false

        chatService.broadcast(roomId, userId, ReceivedMessage(roomId, userId, "hi", "JOIN"), "u")

        verify(exactly = 0) { redisTemplate.convertAndSend(eq("user:$memberId"), any<String>()) }
    }

    @Test
    fun shouldNotSendMessageIfRoomNotInInstanceMemory() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId) } returns true
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { roomRepository.findById(roomId) } returns Optional.of(RoomEntity(id = roomId, name = "r", type = RoomType.GROUP))

        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.attributes } returns hashMapOf("userId" to userId)

        chatService.joinRoom(roomId, session)
        chatService.sessionsInRooms.remove(roomId)

        clearMocks(session)

        chatService.broadcast(roomId, userId, ReceivedMessage(roomId, userId, "hello", "MESSAGE"), "u")

        verify(exactly = 0) { session.sendMessage(any()) }
    }
}
