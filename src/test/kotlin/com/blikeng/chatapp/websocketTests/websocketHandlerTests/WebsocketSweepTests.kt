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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.assertEquals

@ExtendWith(MockKExtension::class)
class WebsocketSweepTests {
    // ==========================
    // Tests for ChatWebSocketHandler stale session sweep and ping handling.
    // ==========================

    @MockK private lateinit var chatService: ChatService
    @MockK private lateinit var wsRateLimitService: WsRateLimitService
    @MockK private lateinit var sessionRegistry: SessionRegistry
    @MockK private lateinit var friendsService: FriendService
    @MockK private lateinit var presenceHandler: PresenceHandler

    private val objectMapper = jacksonObjectMapper()
    private lateinit var handler: ChatWebSocketHandler

    @BeforeEach
    fun setup() {
        handler = ChatWebSocketHandler(chatService, objectMapper, wsRateLimitService, sessionRegistry, ttlMs = 30_000)
        every { wsRateLimitService.tryConsumeMessage(any()) } returns true
    }

    private fun lastPingMap(): ConcurrentHashMap<String, Long> =
        ReflectionTestUtils.getField(handler, "lastPing") as ConcurrentHashMap<String, Long>

    // ==========================
    // Stale session sweep
    // ==========================
    @Test
    fun shouldCloseStaleSessionDuringSweep() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.id } returns "stale"
        every { session.close(CloseStatus.SESSION_NOT_RELIABLE) } just Runs
        every { sessionRegistry.getSessionById("stale") } returns session

        lastPingMap().clear()
        lastPingMap()["stale"] = System.currentTimeMillis() - 100_000

        handler.sweepStaleSessions()

        verify(exactly = 1) { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
    }

    @Test
    fun shouldRemoveMissingStaleSessionFromLastPingDuringSweep() {
        every { sessionRegistry.getSessionById("stale") } returns null

        lastPingMap().clear()
        lastPingMap()["stale"] = System.currentTimeMillis() - 100_000

        handler.sweepStaleSessions()

        assertFalse(lastPingMap().containsKey("stale"))
    }

    @Test
    fun shouldIgnoreNonStaleSessionDuringSweep() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { sessionRegistry.getSessionById(any()) } returns session

        lastPingMap().clear()
        lastPingMap()["fresh"] = System.currentTimeMillis()

        handler.sweepStaleSessions()

        verify(exactly = 0) { session.close(any()) }
    }

    @Test
    fun shouldContinueWhenClosingStaleSessionFails() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.id } returns "stale"
        every { session.close(CloseStatus.SESSION_NOT_RELIABLE) } throws RuntimeException("boom")
        every { sessionRegistry.getSessionById("stale") } returns session

        lastPingMap().clear()
        lastPingMap()["stale"] = System.currentTimeMillis() - 100_000

        assertDoesNotThrow { handler.sweepStaleSessions() }

        verify(exactly = 1) { session.close(CloseStatus.SESSION_NOT_RELIABLE) }
    }

    // ==========================
    // Ping handling
    // ==========================
    @Test
    fun shouldUpdateLastPingAndReplyWithPongWhenPingReceived() {
        val session = mockk<WebSocketSession>(relaxed = true)
        every { session.id } returns "ping-session"
        every { session.attributes } returns mutableMapOf("userId" to UUID.randomUUID(), "username" to "user")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        lastPingMap().clear()
        val before = System.currentTimeMillis()

        handler.handleMessage(session, TextMessage("""{"type":"PING","roomId":"${UUID.randomUUID()}","message":""}"""))

        verify(exactly = 1) { session.sendMessage(any()) }
        assertEquals("pong", msgSlot.captured.payload)
        assertTrue(lastPingMap().containsKey("ping-session"))
        assertTrue(lastPingMap()["ping-session"]!! >= before)
    }
}