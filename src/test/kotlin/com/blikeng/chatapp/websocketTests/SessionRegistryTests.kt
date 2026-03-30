package com.blikeng.chatapp.websocketTests

import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.websocket.SessionRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.socket.WebSocketSession
import java.util.*

class SessionRegistryTests {
    private lateinit var sessionRegistry: SessionRegistry
    private lateinit var presenceHandler: PresenceHandler
    private lateinit var chatService: ChatService
    private lateinit var friendService: FriendService
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setup() {
        presenceHandler = mockk()
        chatService = mockk()
        friendService = mockk()
        meterRegistry = SimpleMeterRegistry()

        every { presenceHandler.userConnected(any()) } returns 1L
        every { presenceHandler.userDisconnected(any()) } returns 0L

        sessionRegistry = SessionRegistry(presenceHandler, friendService, chatService, meterRegistry)
    }

    @Test
    fun shouldRegisterSession() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.id } returns userId.toString()
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs

        sessionRegistry.registerSession(userId, session)

        assertEquals(setOf(session), sessionRegistry.users[userId])
        verify(exactly = 1) { presenceHandler.userConnected(userId) }
    }

    @Test
    fun shouldRegisterMultipleSessionsForSameUser() {
        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.id } returns userId.toString();
        every { session2.id } returns userId.toString();
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs

        sessionRegistry.registerSession(userId, session1)
        sessionRegistry.registerSession(userId, session2)

        assertNotNull(sessionRegistry.users[userId])
        assertEquals(2, sessionRegistry.users[userId]?.size)
        assertTrue(sessionRegistry.users[userId]?.contains(session1) == true)
        assertTrue(sessionRegistry.users[userId]?.contains(session2) == true)

        verify(exactly = 2) { presenceHandler.userConnected(userId) }
    }

    @Test
    fun shouldRemoveSessionAndRemoveUserIfLastSessionClosed() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.id } returns userId.toString()
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs
        every { chatService.removeSessionFromRooms(any()) } just Runs

        sessionRegistry.registerSession(userId, session)
        sessionRegistry.removeSession(userId, session)

        assertNull(sessionRegistry.users[userId])
        verify(exactly = 1) { presenceHandler.userDisconnected(userId) }
    }

    @Test
    fun shouldKeepUserWhenRemovingOneOfMultipleSessions() {
        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.id } returns userId.toString()
        every { session2.id } returns userId.toString()
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs
        every { chatService.removeSessionFromRooms(any()) } just Runs

        sessionRegistry.registerSession(userId, session1)
        sessionRegistry.registerSession(userId, session2)

        sessionRegistry.removeSession(userId, session1)

        assertNotNull(sessionRegistry.users[userId])
        assertEquals(1, sessionRegistry.users[userId]?.size)
        assertTrue(sessionRegistry.users[userId]?.contains(session2) == true)

        verify(exactly = 1) { presenceHandler.userDisconnected(userId) }
    }

    @Test
    fun shouldKeepUserWhenRemovingSessionThatWasNotPresent() {
        val userId = UUID.randomUUID()
        val existingSession = mockk<WebSocketSession>()
        val otherSession = mockk<WebSocketSession>()

        every { existingSession.id } returns userId.toString()
        every { otherSession.id } returns userId.toString()
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs
        every { chatService.removeSessionFromRooms(any()) } just Runs

        sessionRegistry.registerSession(userId, existingSession)
        sessionRegistry.removeSession(userId, otherSession)

        assertNotNull(sessionRegistry.users[userId])
        assertEquals(1, sessionRegistry.users[userId]?.size)
        assertTrue(sessionRegistry.users[userId]?.contains(existingSession) == true)

        verify(exactly = 0) { presenceHandler.userDisconnected(userId) }
    }

    @Test
    fun shouldDoNothingWhenRemovingSessionForUnknownUser() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        sessionRegistry.removeSession(userId, session)

        assertNull(sessionRegistry.users[userId])
        verify(exactly = 0) { presenceHandler.userDisconnected(userId) }
    }

    @Test
    fun shouldRegisterAndEvaluateGauges() {
        val usersGauge = meterRegistry.get("users").gauge()
        val sessionsGauge = meterRegistry.get("users.sessions").gauge()

        assertEquals(0.0, usersGauge.value())
        assertEquals(0.0, sessionsGauge.value())

        val user1 = UUID.randomUUID()
        val user2 = UUID.randomUUID()

        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()
        val session3 = mockk<WebSocketSession>()

        every { session1.id } returns user1.toString();
        every { session2.id } returns user1.toString();
        every { session3.id } returns user2.toString();
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs

        sessionRegistry.registerSession(user1, session1)
        sessionRegistry.registerSession(user1, session2)
        sessionRegistry.registerSession(user2, session3)

        assertEquals(2.0, usersGauge.value())
        assertEquals(3.0, sessionsGauge.value())
    }

    @Test
    fun shouldNotifyFriendsAndRoomsOnlineWhenFirstSessionRegistered() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.id } returns userId.toString()
        every { friendService.notifyFriends(userId, true) } just Runs
        every { chatService.notifyRoomPresence(userId, true) } just Runs

        sessionRegistry.registerSession(userId, session)

        verify(exactly = 1) { friendService.notifyFriends(userId, true) }
        verify(exactly = 1) { chatService.notifyRoomPresence(userId, true) }
    }

    @Test
    fun shouldNotNotifyWhenNotFirstSession() {
        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.id } returns "s1"
        every { session2.id } returns "s2"
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs
        every { presenceHandler.userConnected(userId) } returnsMany listOf(1L, 2L)

        sessionRegistry.registerSession(userId, session1)
        sessionRegistry.registerSession(userId, session2)

        verify(exactly = 1) { friendService.notifyFriends(userId, true) }
        verify(exactly = 1) { chatService.notifyRoomPresence(userId, true) }
    }

    @Test
    fun shouldNotifyFriendsAndRoomsOfflineWhenLastSessionRemoved() {
        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.id } returns userId.toString()
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs
        every { chatService.removeSessionFromRooms(any()) } just Runs

        sessionRegistry.registerSession(userId, session)
        sessionRegistry.removeSession(userId, session)

        verify(exactly = 1) { friendService.notifyFriends(userId, false) }
        verify(exactly = 1) { chatService.notifyRoomPresence(userId, false) }
    }

    @Test
    fun shouldNotNotifyOfflineWhenUserHasRemainingSessions() {
        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.id } returns "s1"
        every { session2.id } returns "s2"
        every { friendService.notifyFriends(any(), any()) } just Runs
        every { chatService.notifyRoomPresence(any(), any()) } just Runs
        every { chatService.removeSessionFromRooms(any()) } just Runs
        every { presenceHandler.userConnected(userId) } returnsMany listOf(1L, 2L)
        every { presenceHandler.userDisconnected(userId) } returns 1L

        sessionRegistry.registerSession(userId, session1)
        sessionRegistry.registerSession(userId, session2)
        sessionRegistry.removeSession(userId, session1)

        verify(exactly = 0) { friendService.notifyFriends(userId, false) }
        verify(exactly = 0) { chatService.notifyRoomPresence(userId, false) }
    }

    @Test
    fun shouldReturnSessionWhenSessionIdExists() {
        val session = mockk<WebSocketSession>()
        val sessionId = "s1"

        val map = ReflectionTestUtils.getField(sessionRegistry, "sessionIndex") as MutableMap<String, WebSocketSession>
        map[sessionId] = session

        val result = sessionRegistry.getSessionById(sessionId)

        assertEquals(session, result)
    }

    @Test
    fun shouldReturnNullWhenSessionIdDoesNotExist() {
        val result = sessionRegistry.getSessionById("missing")

        assertNull(result)
    }
}