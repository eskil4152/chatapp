package com.blikeng.chatapp.websocketTests.websocketHandlerTests

import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.security.ratelimit.WsRateLimitService
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.websocket.ChatWebSocketHandler
import com.blikeng.chatapp.websocket.SessionRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class WebsocketMessageTests {
    // ==========================
    // Tests for ChatWebSocketHandler message routing, input validation,
    // exception-to-error-response mapping, and rate limiting.
    // ==========================

    @MockK private lateinit var chatService: ChatService
    @MockK private lateinit var wsRateLimitService: WsRateLimitService
    @MockK private lateinit var sessionRegistry: SessionRegistry
    @MockK private lateinit var friendsService: FriendService
    @MockK private lateinit var presenceHandler: PresenceHandler
    @MockK private lateinit var session: WebSocketSession

    private val objectMapper = jacksonObjectMapper()
    private lateinit var handler: ChatWebSocketHandler

    @BeforeEach
    fun setup() {
        handler = ChatWebSocketHandler(chatService, objectMapper, wsRateLimitService, sessionRegistry, ttlMs = 30_000)
        every { wsRateLimitService.tryConsumeMessage(any()) } returns true
    }

    // ==========================
    // Message routing
    // ==========================
    @Test
    fun shouldSendJoinMessage() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "JOIN").put("message", "m").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { chatService.joinRoom(any(), any()) } returns Unit
        every { chatService.broadcast(any(), any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify(exactly = 1) { chatService.joinRoom(any(), any()) }
        verify(exactly = 1) { chatService.broadcast(any(), any(), any()) }
        verify(exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendLeaveMessage() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "LEAVE").put("message", "m").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { chatService.leaveRoom(any(), any()) } returns Unit
        every { chatService.broadcast(any(), any(), any()) } just Runs
        every { session.id } returns "test-session-id"
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 0) { chatService.joinRoom(any(), any()) }
        verify(exactly = 1) { chatService.broadcast(any(), any(), any()) }
        verify(exactly = 1) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendMessage() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("message", "m").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { chatService.broadcast(any(), any(), any()) } returns Unit
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 0) { chatService.joinRoom(any(), any()) }
        verify(exactly = 1) { chatService.broadcast(any(), any(), any()) }
        verify(exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldReceivePing() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "PING").put("message", "").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 0) { chatService.joinRoom(any(), any()) }
        verify(exactly = 0) { chatService.broadcast(any(), any(), any()) }
        verify(exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    // ==========================
    // Validation and missing attributes
    // ==========================
    @Test
    fun shouldSendErrorWithoutUsername() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "JOIN").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("userId" to UUID.randomUUID())
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(401, json["code"].asInt())
        assertEquals("No username found", json["message"].asText())
        verify(exactly = 0) { chatService.joinRoom(any(), any()) }
    }

    @Test
    fun shouldFailToSendMessageWithoutUserId() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "JOIN").put("message", "m").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u")
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(401, json["code"].asInt())
        assertEquals("No userID found", json["message"].asText())
    }

    @Test
    fun shouldFailToSendMessageWithInvalidType() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "").put("message", "m").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Invalid message type", json["message"].asText())
    }

    @Test
    fun shouldFailToSendMessageWithInvalidRoomId() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("message", "m").put("roomId", "").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Invalid room ID", json["message"].asText())
    }

    @Test
    fun shouldSendWsErrorWhenMessageFieldIsMissing() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.id } returns "s1"
        every { session.attributes } returns mutableMapOf("userId" to UUID.randomUUID(), "username" to "user")
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, TextMessage("""{"type":"MESSAGE","roomId":"${UUID.randomUUID()}"}"""))

        val payload = msgSlot.captured.payload
        assertTrue(payload.contains("400"))
        assertTrue(payload.contains("Missing message field"))
    }

    @Test
    fun shouldSendWsErrorWhenMessageFieldIsNull() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.id } returns "s1"
        every { session.attributes } returns mutableMapOf("userId" to UUID.randomUUID(), "username" to "user")
        every { session.isOpen } returns true

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, TextMessage("""{"type":"MESSAGE","roomId":"${UUID.randomUUID()}","message":null}"""))

        assertTrue(msgSlot.captured.payload.contains("400"))
    }

    // ==========================
    // Exception mapping
    // ==========================
    @Test
    fun shouldSendErrorForResponseStatusException() {
        val roomId = UUID.randomUUID().toString()
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "JOIN").put("roomId", roomId).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { chatService.joinRoom(any(), any()) } throws ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(403, json["code"].asInt())
        assertEquals("Not permitted", json["message"].asText())
        verify(exactly = 0) { chatService.broadcast(any(), any(), any()) }
    }

    @Test
    fun shouldSendErrorForIllegalArgumentException() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("roomId", UUID.randomUUID().toString()).put("message", "m").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { chatService.broadcast(any(), any(), any()) } throws IllegalArgumentException("Bad request X")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Bad request X", json["message"].asText())
    }

    @Test
    fun shouldSendErrorForUnknownException() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("roomId", UUID.randomUUID().toString()).put("message", "m").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { chatService.broadcast(any(), any(), any()) } throws RuntimeException("exception")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(500, json["code"].asInt())
        assertEquals("Internal error", json["message"].asText())
    }

    @Test
    fun shouldNotSendErrorWhenSessionClosed() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("roomId", UUID.randomUUID().toString()).put("message", "m").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns false
        every { chatService.broadcast(any(), any(), any()) } throws RuntimeException("exception")
        every { session.sendMessage(any()) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldUseExceptionMessageWhenReasonIsNull() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "JOIN").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { chatService.joinRoom(any(), any()) } throws ResponseStatusException(HttpStatus.BAD_REQUEST)

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertTrue(json["message"].asText().contains("400 BAD_REQUEST"))
    }

    @Test
    fun shouldFallbackToBadRequestWhenIllegalArgumentMessageNull() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("message", "m").put("roomId", UUID.randomUUID().toString()).toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { chatService.broadcast(any(), any(), any()) } throws IllegalArgumentException()

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Bad request", json["message"].asText())
    }

    // ==========================
    // Rate limiting
    // ==========================
    @Test
    fun shouldSendRateLimitErrorForMessage() {
        val payload = TextMessage(objectMapper.createObjectNode()
            .put("type", "MESSAGE").put("roomId", UUID.randomUUID().toString()).put("message", "m").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { wsRateLimitService.tryConsumeMessage(any()) } returns false

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 0) { chatService.broadcast(any(), any(), any()) }

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(429, json["code"].asInt())
        assertEquals("Rate limit exceeded", json["message"].asText())
    }

    @Test
    fun shouldHandleSyncMessage() {
        val userId = UUID.randomUUID()
        val payload = TextMessage(objectMapper.createObjectNode().put("type", "SYNC").toString())

        every { session.attributes } returns mutableMapOf("username" to "u", "userId" to userId)
        every { sessionRegistry.sendSnapshots(userId, session) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 1) { sessionRegistry.sendSnapshots(userId, session) }
    }
}