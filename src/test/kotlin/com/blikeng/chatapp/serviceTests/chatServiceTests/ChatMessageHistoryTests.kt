package com.blikeng.chatapp.serviceTests.chatServiceTests

import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
import com.blikeng.chatapp.dtos.messaging.SendMessageDTO
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
class ChatMessageHistoryTests {
    // ==========================
    // Tests for ChatService message history and pending messages.
    // Verifies:
    // - Persisted messages returned in order for page 0 and later pages
    // - Redis pending messages merged and deduplicated on page 0
    // - Encrypted messages returned as ciphertext payloads
    // - fetchAllMessages decrypts and sends over session
    // - Null Redis range handled gracefully
    // - Gauge reflects room count
    // ==========================

    @MockK lateinit var chatRepository: ChatRepository
    @MockK lateinit var roomRepository: RoomRepository
    @MockK lateinit var userRoomRepository: UserRoomRepository
    @MockK lateinit var encrypt: ChatEncrypt
    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var rabbitTemplate: RabbitTemplate
    @MockK lateinit var listOps: ListOperations<String, String>
    @MockK lateinit var presenceHandler: PresenceHandler
    @MockK(relaxed = true) lateinit var meterRegistry: MeterRegistry

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
    // Persisted and pending messages
    // ==========================
    @Test
    fun shouldGetPersistedRoomMessagesPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat1 = ChatEntity(roomId = room.id, user = user, message = "Hello", timestamp = Timestamp(System.currentTimeMillis() - 1000))
        val chat2 = ChatEntity(roomId = room.id, user = user, message = "Hello again", timestamp = Timestamp(System.currentTimeMillis()))

        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(chat2, chat1))
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
        val chat = ChatEntity(roomId = room.id, user = user, message = "Older message", timestamp = Timestamp(System.currentTimeMillis()))

        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(chat))

        val result = chatService.getRoomMessages(room.id, 1, 25)

        assertEquals(1, result.size)
        assertEquals("Older message", result[0].message)
        verify(exactly = 0) { listOps.range(any(), any(), any()) }
    }

    @Test
    fun shouldIncludePendingRedisMessagesOnPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(emptyList())

        val pending = RabbitMessageDTO(roomId = room.id, userId = user.id, username = user.username, message = "from redis")
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns listOf(objectMapper.writeValueAsString(pending))

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(1, result.size)
        assertEquals("from redis", result[0].message)
    }

    @Test
    fun shouldMergePersistedAndPendingMessagesOnPageZero() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        val persisted = ChatEntity(roomId = room.id, user = user, message = "persisted", timestamp = Timestamp(System.currentTimeMillis() - 1000))
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(persisted))

        val pending = RabbitMessageDTO(roomId = room.id, userId = user.id, username = user.username, message = "pending")
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns listOf(objectMapper.writeValueAsString(pending))

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(2, result.size)
        assertTrue(result.any { it.message == "persisted" })
        assertTrue(result.any { it.message == "pending" })
    }

    @Test
    fun shouldReturnEmptyListWhenRedisRangeReturnsNull() {
        val room = RoomEntity(name = "r", type = RoomType.GROUP)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(emptyList())
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns null

        assertTrue(chatService.getRoomMessages(room.id, 0, 25).isEmpty())
    }

    // ==========================
    // Encrypted history
    // ==========================
    @Test
    fun shouldReturnEncryptedMessagesAsEncryptedPayloadInHistory() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")
        val chat = ChatEntity(
            roomId = room.id, user = user, message = null,
            ciphertext = "cipher".toByteArray(), nonce = "nonce".toByteArray(),
            keyVersion = 1, timestamp = Timestamp(System.currentTimeMillis())
        )

        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(chat))
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns emptyList()

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(1, result.size)
        assertNull(result[0].message)
        assertArrayEquals("cipher".toByteArray(), result[0].ciphertext)
        assertArrayEquals("nonce".toByteArray(), result[0].nonce)
        assertEquals(1, result[0].keyVersion)
    }

    // ==========================
    // fetchAllMessages
    // ==========================
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
            timestamp = Timestamp(System.currentTimeMillis())
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
            id = UUID.randomUUID(), roomId = room.id, userId = user.id, username = user.username,
            message = null, keyVersion = null, ciphertext = null, nonce = null,
            timestamp = Timestamp(System.currentTimeMillis())
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
            id = UUID.randomUUID(), roomId = room.id, userId = user.id, username = user.username,
            message = "Hello", keyVersion = null, ciphertext = null, nonce = null,
            timestamp = Timestamp(System.currentTimeMillis())
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
    fun shouldFailToGetMessagesWhenUsingInvalidParameters() {
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

        chatService.rooms[UUID.randomUUID()] = CopyOnWriteArraySet(mutableSetOf(mockk()))
        chatService.rooms[UUID.randomUUID()] = CopyOnWriteArraySet(mutableSetOf(mockk()))

        assertEquals(2.0, roomsGauge.value())
    }
}