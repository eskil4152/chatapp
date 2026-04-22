package com.blikeng.chatapp.messaging.redis

import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.websocket.SessionRegistry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import java.util.*
import java.util.concurrent.Executor

// ==========================
// Broadcasts Redis-delivered room messages to WebSocket sessions
// connected to the current application instance.
// Removes sessions from the room when sending fails.
// ==========================
@Component
class LocalBroadcaster(
    private val chatService: ChatService,
    private val sessionRegistry: SessionRegistry,
    @Qualifier("broadcastExecutor") private val broadcastExecutor: Executor,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun broadcastRaw(roomId: UUID, payload: String) {
        val message = TextMessage(payload)
        chatService.sessionsInRooms[roomId]?.forEach { session ->
            broadcastExecutor.execute {
                synchronized(session) {
                    if (session.isOpen) {
                        try {
                            session.sendMessage(message)
                        } catch (e: Exception) {
                            logger.error("Failed to send message to session {}", session.id, e)
                            chatService.leaveRoom(roomId, session)
                        }
                    }
                }
            }
        }
    }

    fun sendToUser(userId: UUID, payload: String) {
        val message = TextMessage(payload)
        sessionRegistry.users[userId]?.forEach { session ->
            broadcastExecutor.execute {
                synchronized(session) {
                    if (session.isOpen) {
                        try {
                            session.sendMessage(message)
                        } catch (e: Exception) {
                            logger.error("Failed to send message to user session {}", session.id, e)
                        }
                    }
                }
            }
        }
    }
}