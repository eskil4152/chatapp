package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.messaging.redis.PresenceHandler
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Component
class SessionRegistry(
    private val presenceHandler: PresenceHandler,
    meterRegistry: MeterRegistry
) {
    val users = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    private val sessionIndex = ConcurrentHashMap<String, WebSocketSession>()

    init {
        meterRegistry.gauge("users", users) { it.size.toDouble() }
        meterRegistry.gauge("users.sessions", users) {
            it.values.sumOf { sessions -> sessions.size.toDouble() }
        }
    }

    fun registerSession(userId: UUID, session: WebSocketSession) {
        users.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        sessionIndex[session.id] = session
        presenceHandler.userConnected(userId)
    }

    fun removeSession(userId: UUID, session: WebSocketSession) {
        val sessions = users[userId] ?: return

        val removed = sessions.remove(session)
        if (!removed) return

        if (sessions.isEmpty()) {
            users.remove(userId, sessions)
        }

        sessionIndex.remove(session.id)
        presenceHandler.userDisconnected(userId)
    }

    fun getSessionById(sessionId: String): WebSocketSession? = sessionIndex[sessionId]
}