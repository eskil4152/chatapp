package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.dtos.websocket.WsPendingInviteSnapshot
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.InviteService
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class SessionRegistry(
    private val presenceHandler: PresenceHandler,
    private val friendService: FriendService,
    private val chatService: ChatService,
    private val inviteService: InviteService,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry
) {
    val users = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    private val sessionIndex = ConcurrentHashMap<String, WebSocketSession>()

    init {
        meterRegistry.gauge("users", users) { it.size.toDouble() }
        meterRegistry.gauge("user.sessions", users) {
            it.values.sumOf { sessions -> sessions.size.toDouble() }
        }
    }

    fun registerSession(userId: UUID, session: WebSocketSession) {
        users.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        val count = presenceHandler.userConnected(userId)

        sessionIndex[session.id] = session

        if (count.toInt() == 1) {
            friendService.notifyFriends(userId, online = true)
            chatService.notifyRoomPresence(userId, online = true)
        }
    }

    @Async("snapshotExecutor")
    fun sendSnapshots(userId: UUID, session: WebSocketSession) {
        val presencePayload = objectMapper.writeValueAsString(friendService.getOnlineFriends(userId))
        trySend(session, presencePayload)

        val invitePayload = objectMapper.writeValueAsString(WsPendingInviteSnapshot(invites = inviteService.getPendingInvites(userId)))
        trySend(session, invitePayload)
    }

    private fun trySend(session: WebSocketSession, payload: String) {
        synchronized(session) {
            if (!session.isOpen) return
            try {
                session.sendMessage(TextMessage(payload))
            } catch (_: IOException) {
                // Client disconnected between isOpen check and send — ignore
            }
        }
    }

    fun removeSession(userId: UUID, session: WebSocketSession) {
        val sessions = users[userId] ?: return
        val removed = sessions.remove(session)
        if (!removed) return
        if (sessions.isEmpty()) users.remove(userId, sessions)

        sessionIndex.remove(session.id)
        chatService.removeSessionFromRooms(session)

        val count = presenceHandler.userDisconnected(userId)

        if (count.toInt() == 0) {
            friendService.notifyFriends(userId, online = false)
            chatService.notifyRoomPresence(userId, online = false)
        }
    }

    fun getSessionById(sessionId: String): WebSocketSession? = sessionIndex[sessionId]
}