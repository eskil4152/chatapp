package com.blikeng.chatapp.serviceTests.chatServiceTests

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
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
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageImpl
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.Collections.emptyList
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CopyOnWriteArraySet

@ExtendWith(MockKExtension::class)
class ChatMessageHistoryTests {
    // ==========================
    // Tests for ChatService message history and pending messages.
    // Verifies:
    // - Persisted messages returned in order for page 0 and later pages
    // - Redis pending messages merged and deduplicated on page 0
    // - Encrypted messages decrypted server-side before returning
    // - Null Redis range handled gracefully
    // - Gauge reflects room count
    // ==========================

    @MockK lateinit var userService: UserService

    @MockK lateinit var chatRepository: ChatRepository

    @MockK lateinit var roomRepository: RoomRepository

    @MockK lateinit var userRoomRepository: UserRoomRepository

    @MockK lateinit var encrypt: ChatEncrypt

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK lateinit var rabbitTemplate: RabbitTemplate

    @MockK lateinit var listOps: ListOperations<String, String>

    @MockK lateinit var presenceHandler: PresenceHandler

    @MockK(relaxed = true)
    lateinit var meterRegistry: MeterRegistry

    @InjectMockKs lateinit var chatService: ChatService
    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())

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

        val chat1 = ChatEntity(roomId = room.id, user = user, message = "Hello", timestamp = Instant.now().minusMillis(1000))
        val chat2 = ChatEntity(roomId = room.id, user = user, message = "Hello again", timestamp = Instant.now())

        every { roomRepository.findById(room.id) } returns Optional.of(room)
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
        val chat = ChatEntity(roomId = room.id, user = user, message = "Older message", timestamp = Instant.now())

        every { roomRepository.findById(room.id) } returns Optional.of(room)
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

        every { roomRepository.findById(room.id) } returns Optional.of(room)
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

        val persisted = ChatEntity(roomId = room.id, user = user, message = "persisted", timestamp = Instant.now().minusMillis(1000))
        every { roomRepository.findById(room.id) } returns Optional.of(room)
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
        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(emptyList())
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns null

        assertTrue(chatService.getRoomMessages(room.id, 0, 25).isEmpty())
    }

    @Test
    fun shouldFailToGetMessagesWhenUsingInvalidParameters() {
        val pageNumberException =
            assertThrows<ApiException> {
                chatService.getRoomMessages(UUID.randomUUID(), -1, 25)
            }
        assertEquals(HttpStatus.BAD_REQUEST, pageNumberException.status)
        assertEquals(ErrorMessages.INVALID_PARAMETERS, pageNumberException.message)

        val pageSizeException =
            assertThrows<ApiException> {
                chatService.getRoomMessages(UUID.randomUUID(), 0, 250)
            }
        assertEquals(HttpStatus.BAD_REQUEST, pageSizeException.status)
        assertEquals(ErrorMessages.INVALID_PARAMETERS, pageSizeException.message)
    }

    @Test
    fun shouldThrowWhenRoomNotFoundOnGetMessages() {
        val roomId = UUID.randomUUID()
        every { roomRepository.findById(roomId) } returns Optional.empty()

        val ex = assertThrows<ApiException> { chatService.getRoomMessages(roomId, 0, 25) }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    // ==========================
    // Encrypted history
    // ==========================
    @Test
    fun shouldReturnEncryptedMessagesAsPlaintextInHistory() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val cipher = "cipher".toByteArray()
        val nonce = "nonce".toByteArray()

        val chat =
            ChatEntity(
                roomId = room.id,
                user = user,
                message = null,
                ciphertext = cipher,
                nonce = nonce,
                keyVersion = 1,
                timestamp = Instant.now(),
            )

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(chat))
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns emptyList()
        every { encrypt.decrypt(cipher, nonce, configureAad(room.id, chat.id, user.id)) } returns "Hello, this is a text"

        val result = chatService.getRoomMessages(room.id, 0, 25)

        assertEquals(1, result.size)
        assertEquals("Hello, this is a text", result[0].message)
    }

    @Test
    fun shouldThrowWhenBufferedEncryptedMessageMissingCiphertext() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(emptyList())

        val pending = RabbitMessageDTO(roomId = room.id, userId = user.id, username = user.username)
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns listOf(objectMapper.writeValueAsString(pending))

        val ex = assertThrows<ApiException> { chatService.getRoomMessages(room.id, 0, 25) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldThrowWhenBufferedEncryptedMessageMissingNonce() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "")

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(emptyList())

        val pending =
            RabbitMessageDTO(
                roomId = room.id,
                userId = user.id,
                username = user.username,
                ciphertext = "cipher".toByteArray(),
                nonce = null,
            )
        every { listOps.range("chat.peek.${room.id}", 0L, -1L) } returns listOf(objectMapper.writeValueAsString(pending))

        val ex = assertThrows<ApiException> { chatService.getRoomMessages(room.id, 0, 25) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldThrowWhenPersistedEncryptedMessageHasNullCiphertext() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat =
            ChatEntity(roomId = room.id, user = user, message = null, ciphertext = null, nonce = "nonce".toByteArray(), keyVersion = 1)

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(chat))

        val ex = assertThrows<ApiException> { chatService.getRoomMessages(room.id, 0, 25) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldThrowWhenPersistedEncryptedMessageHasNullNonce() {
        val room = RoomEntity(name = "r", encrypted = true, keyVersion = 1, type = RoomType.GROUP)
        val user = UserEntity(username = "u", password = "")

        val chat =
            ChatEntity(roomId = room.id, user = user, message = null, ciphertext = "cipher".toByteArray(), nonce = null, keyVersion = 1)

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { chatRepository.findByRoomIdOrderByTimestampDesc(eq(room.id), any()) } returns PageImpl(listOf(chat))

        val ex = assertThrows<ApiException> { chatService.getRoomMessages(room.id, 0, 25) }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    // ==========================
    // Meter registry
    // ==========================
    @Test
    fun shouldRegisterAndEvaluateChatGauges() {
        val meterRegistry = SimpleMeterRegistry()

        val chatService =
            ChatService(
                chatRepository = chatRepository,
                roomRepository = roomRepository,
                userRoomRepository = userRoomRepository,
                encrypt = encrypt,
                redisTemplate = redisTemplate,
                rabbitTemplate = rabbitTemplate,
                objectMapper = objectMapper,
                presenceHandler = presenceHandler,
                meterRegistry = meterRegistry,
                userService = userService,
            )

        val roomsGauge = meterRegistry.get("app.rooms.active").gauge()
        assertEquals(0.0, roomsGauge.value())

        chatService.sessionsInRooms[UUID.randomUUID()] = CopyOnWriteArraySet(mutableSetOf(mockk()))
        chatService.sessionsInRooms[UUID.randomUUID()] = CopyOnWriteArraySet(mutableSetOf(mockk()))

        assertEquals(2.0, roomsGauge.value())
    }
}
