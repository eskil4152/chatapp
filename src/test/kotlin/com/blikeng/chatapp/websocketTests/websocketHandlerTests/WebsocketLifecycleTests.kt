package com.blikeng.chatapp.websocketTests.websocketHandlerTests

import com.blikeng.chatapp.security.ratelimit.WsRateLimitService
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.websocket.ChatWebSocketHandler
import com.blikeng.chatapp.websocket.SessionRegistry
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class WebsocketLifecycleTests {
    // ==========================
    // Tests for ChatWebSocketHandler connection lifecycle and graceful shutdown.
    // Presence and notification logic is tested in SessionRegistryTests.
    // ==========================

    @MockK private lateinit var chatService: ChatService
    @MockK private lateinit var wsRateLimitService: WsRateLimitService
    @MockK private lateinit var sessionRegistry: SessionRegistry
    @MockK private lateinit var session: WebSocketSession

    private val objectMapper = jacksonObjectMapper()
    private lateinit var handler: ChatWebSocketHandler

    @BeforeEach
    fun setup() {
        handler = ChatWebSocketHandler(chatService, objectMapper, wsRateLimitService, sessionRegistry, ttlMs = 30_000)
        every { wsRateLimitService.tryConsumeMessage(any()) } returns true
    }

    // ==========================
    // Connection established
    // ==========================
    @Test
    fun connectionEstablishedShouldRegisterSession() {
        val userId = UUID.randomUUID()
        val attributes: MutableMap<String, Any> = mutableMapOf("userId" to userId, "username" to "u")

        every { sessionRegistry.registerSession(any(), any()) } just Runs
        every { sessionRegistry.sendFriendPresenceSnapshot(any(), any()) } just Runs
        every { sessionRegistry.sendPendingInviteSnapshot(any(), any()) } just Runs
        every { session.attributes } returns attributes
        every { session.id } returns "123"

        handler.afterConnectionEstablished(session)

        verify(exactly = 1) { sessionRegistry.registerSession(userId, session) }
        verify(exactly = 1) { sessionRegistry.sendFriendPresenceSnapshot(userId, session) }
        verify(exactly = 0) { chatService.joinRoom(any(), any()) }
    }

    @Test
    fun connectionEstablishedShouldFail() {
        every { session.attributes } returns mutableMapOf("username" to "u")

        val exception = assertFailsWith<ResponseStatusException> {
            handler.afterConnectionEstablished(session)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("No userID found", exception.reason)
        verify(exactly = 0) { sessionRegistry.registerSession(any(), any()) }
    }

    // ==========================
    // Connection closed
    // ==========================
    @Test
    fun connectionClosedShouldUnregisterSession() {
        val userId = UUID.randomUUID()
        val attributes: MutableMap<String, Any> = mutableMapOf("userId" to userId, "username" to "u")

        every { sessionRegistry.registerSession(any(), any()) } just Runs
        every { sessionRegistry.sendFriendPresenceSnapshot(any(), any()) } just Runs
        every { sessionRegistry.sendPendingInviteSnapshot(any(), any()) } just Runs
        every { sessionRegistry.removeSession(any(), any()) } just Runs
        every { session.attributes } returns attributes
        every { session.id } returns "123"

        handler.afterConnectionEstablished(session)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(exactly = 1) { sessionRegistry.registerSession(userId, session) }
        verify(exactly = 1) { sessionRegistry.removeSession(userId, session) }
    }

    @Test
    fun connectionClosedShouldFail() {
        every { session.attributes } returns mutableMapOf("username" to "u")

        val ex = assertFailsWith<ResponseStatusException> {
            handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("No userID found", ex.reason)
        verify(exactly = 0) { sessionRegistry.removeSession(any(), any()) }
    }

    // ==========================
    // Shutdown
    // ==========================
    @Test
    fun shouldCloseAllSessionsOnShutdown() {
        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)

        every { session1.close(CloseStatus.GOING_AWAY) } just Runs
        every { session2.close(CloseStatus.GOING_AWAY) } just Runs
        every { sessionRegistry.users } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(UUID.randomUUID(), mutableSetOf(session1))
            put(UUID.randomUUID(), mutableSetOf(session2))
        }

        handler.shutdown()

        verify(exactly = 1) { session1.close(CloseStatus.GOING_AWAY) }
        verify(exactly = 1) { session2.close(CloseStatus.GOING_AWAY) }
    }

    @Test
    fun shouldContinueClosingSessionsWhenOneFailsDuringShutdown() {
        val session1 = mockk<WebSocketSession>(relaxed = true)
        val session2 = mockk<WebSocketSession>(relaxed = true)

        every { session1.id } returns "s1"
        every { session2.id } returns "s2"
        every { session1.close(CloseStatus.GOING_AWAY) } throws RuntimeException("boom")
        every { session2.close(CloseStatus.GOING_AWAY) } just Runs
        every { sessionRegistry.users } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(UUID.randomUUID(), mutableSetOf(session1, session2))
        }

        handler.shutdown()

        verify(exactly = 1) { session1.close(CloseStatus.GOING_AWAY) }
        verify(exactly = 1) { session2.close(CloseStatus.GOING_AWAY) }
    }
}