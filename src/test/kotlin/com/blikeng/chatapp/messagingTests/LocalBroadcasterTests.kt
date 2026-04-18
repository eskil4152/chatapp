package com.blikeng.chatapp.messagingTests

import com.blikeng.chatapp.messaging.redis.LocalBroadcaster
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.websocket.SessionRegistry
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor

@ExtendWith(MockKExtension::class)
class LocalBroadcasterTests {
    // ==========================
    // Tests for LocalBroadcaster. Verifies:
    // - Messages are broadcast to all open sessions in a room
    // - Closed WebSocket sessions are skipped
    // - Sessions are removed when message sending fails
    // - No action occurs when the room does not exist on the instance
    // ==========================
    @InjectMockKs lateinit var broadcaster: LocalBroadcaster

    @MockK lateinit var chatService: ChatService
    @MockK lateinit var sessionRegistry: SessionRegistry
    @MockK lateinit var broadcastExecutor: Executor

    @BeforeEach
    fun setup() {
        every { broadcastExecutor.execute(any()) } answers { firstArg<Runnable>().run() }
    }

    @Test
    fun shouldBroadcastToAllOpenSessionsInRoom() {
        val roomId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.isOpen } returns true
        every { session2.isOpen } returns true
        every { session1.sendMessage(any()) } just Runs
        every { session2.sendMessage(any()) } just Runs
        every { chatService.rooms } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(roomId, CopyOnWriteArraySet(listOf(session1, session2)))
        }

        broadcaster.broadcastRaw(roomId, "hello")

        verify(exactly = 1) { session1.sendMessage(TextMessage("hello")) }
        verify(exactly = 1) { session2.sendMessage(TextMessage("hello")) }
    }

    @Test
    fun shouldSkipClosedSessions() {
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.isOpen } returns false
        every { chatService.rooms } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(roomId, CopyOnWriteArraySet(listOf(session)))
        }

        broadcaster.broadcastRaw(roomId, "hello")

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldRemoveSessionWhenSendFails() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val session = mockk<WebSocketSession>()
        val attrs = hashMapOf<String, Any>("userId" to userId)

        every { session.attributes } returns attrs
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } throws RuntimeException("boom")
        every { session.id } returns userId.toString()

        val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
        rooms[roomId] = CopyOnWriteArraySet(listOf(session))

        every { chatService.rooms } returns rooms
        every { chatService.leaveRoom(any(), any()) } just Runs

        broadcaster.broadcastRaw(roomId, "hello")

        verify(exactly = 1) { chatService.leaveRoom(any(), session) }
    }

    @Test
    fun shouldDoNothingIfRoomIsNotPresentInInstance() {
        val roomId = UUID.randomUUID()

        val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

        every { chatService.rooms } returns rooms

        assertDoesNotThrow {
            broadcaster.broadcastRaw(roomId, "hello")
        }

        assertTrue(rooms.isEmpty())
    }

    @Test
    fun shouldSendToAllOpenUserSessions() {
        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.isOpen } returns true
        every { session2.isOpen } returns true
        every { session1.sendMessage(any()) } just Runs
        every { session2.sendMessage(any()) } just Runs
        every { sessionRegistry.users } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(userId, CopyOnWriteArraySet(listOf(session1, session2)))
        }

        broadcaster.sendToUser(userId, "payload")

        verify(exactly = 1) { session1.sendMessage(TextMessage("payload")) }
        verify(exactly = 1) { session2.sendMessage(TextMessage("payload")) }
    }

    @Test
    fun shouldSkipClosedUserSession() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.isOpen } returns false
        every { sessionRegistry.users } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(userId, CopyOnWriteArraySet(listOf(session)))
        }

        broadcaster.sendToUser(userId, "payload")

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldContinueWhenSendToUserSessionFails() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.isOpen } returns true
        every { session.sendMessage(any()) } throws RuntimeException("error")
        every { session.id } returns userId.toString()
        every { sessionRegistry.users } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(userId, CopyOnWriteArraySet(listOf(session)))
        }

        assertDoesNotThrow { broadcaster.sendToUser(userId, "payload") }
        verify(exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldDoNothingIfUserNotPresentOnInstance() {
        val userId = UUID.randomUUID()

        every { sessionRegistry.users } returns ConcurrentHashMap()

        assertDoesNotThrow {
            broadcaster.sendToUser(userId, "payload")
        }
    }
}