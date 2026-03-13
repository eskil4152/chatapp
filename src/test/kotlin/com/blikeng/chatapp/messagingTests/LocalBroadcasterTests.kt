package com.blikeng.chatapp.messagingTests

import com.blikeng.chatapp.messaging.redis.LocalBroadcaster
import com.blikeng.chatapp.services.ChatService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.test.assertEquals

@ExtendWith(MockKExtension::class)
class LocalBroadcasterTests {
    // ==========================
    // Tests for LocalBroadcaster. Verifies:
    // - Messages are broadcast to all open sessions in a room
    // - Closed WebSocket sessions are skipped
    // - Sessions are removed when message sending fails
    // - No action occurs when the room does not exist on the instance
    // ==========================
    @MockK lateinit var chatService: ChatService

    @InjectMockKs lateinit var broadcaster: LocalBroadcaster

    @Test
    fun shouldBroadcastToAllOpenSessionsInRoom() {
        val roomId = UUID.randomUUID()
        val s1 = mockk<WebSocketSession>()
        val s2 = mockk<WebSocketSession>()

        every { s1.isOpen } returns true
        every { s2.isOpen } returns true
        every { s1.sendMessage(any()) } just Runs
        every { s2.sendMessage(any()) } just Runs
        every { chatService.rooms } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(roomId, CopyOnWriteArraySet(listOf(s1, s2)))
        }

        broadcaster.broadcastRaw(roomId, "hello")

        verify(exactly = 1) { s1.sendMessage(TextMessage("hello")) }
        verify(exactly = 1) { s2.sendMessage(TextMessage("hello")) }
    }

    @Test
    fun shouldSkipClosedSessions() {
        val roomId = UUID.randomUUID()
        val s1 = mockk<WebSocketSession>()

        every { s1.isOpen } returns false
        every { chatService.rooms } returns ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>().apply {
            put(roomId, CopyOnWriteArraySet(listOf(s1)))
        }

        broadcaster.broadcastRaw(roomId, "hello")

        verify(exactly = 0) { s1.sendMessage(any()) }
    }

    @Test
    fun shouldRemoveSessionWhenSendFails() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val s1 = mockk<WebSocketSession>()
        val attrs = hashMapOf<String, Any>("userId" to userId)

        every { s1.attributes } returns attrs
        every { s1.isOpen } returns true
        every { s1.sendMessage(any()) } throws RuntimeException("boom")

        val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
        rooms[roomId] = CopyOnWriteArraySet(listOf(s1))

        every { chatService.rooms } returns rooms
        every { chatService.leaveRoom(any(), any()) } just Runs

        broadcaster.broadcastRaw(roomId, "hello")

        verify(exactly = 1) { chatService.leaveRoom(any(), s1) }
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
}