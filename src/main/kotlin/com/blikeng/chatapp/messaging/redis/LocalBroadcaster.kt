package com.blikeng.chatapp.messaging.redis

import com.blikeng.chatapp.services.ChatService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import java.util.*

// ==========================
// Broadcasts Redis-delivered room messages to WebSocket sessions
// connected to the current application instance.
// Removes sessions from the room when sending fails.
// ==========================
@Component
class LocalBroadcaster(
    private val chatService: ChatService
) {
    private val logger = LoggerFactory.getLogger(this::class.java);

    fun broadcastRaw(roomId: UUID, payload: String) {
        chatService.rooms[roomId]?.forEach { session ->
            synchronized(session) {
                if (session.isOpen) {
                    try {
                        session.sendMessage(TextMessage(payload))
                    } catch (e: Exception) {
                        logger.error("Failed to send message to session {}", session.id, e)
                        chatService.leaveRoom(roomId, session)
                    }
                }
            }
        }
    }
}