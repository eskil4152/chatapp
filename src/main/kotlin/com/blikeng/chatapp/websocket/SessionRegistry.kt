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

    init {
        meterRegistry.gauge("users", users) { it.size.toDouble() }
        meterRegistry.gauge("users.sessions", users) {
            it.values.sumOf { sessions -> sessions.size.toDouble() }
        }
    }

    fun registerSession(userId: UUID, session: WebSocketSession) {
        users.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        presenceHandler.userConnected(userId)
    }

    fun removeSession(userId: UUID, session: WebSocketSession) {
        users[userId]?.remove(session)
        if (users[userId]?.isEmpty() == true) {
            users.remove(userId)
        }
        presenceHandler.userDisconnected(userId)
    }
}