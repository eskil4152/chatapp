package com.blikeng.chatapp.messaging.redis

import com.blikeng.chatapp.services.ChatService
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import java.util.*

@Component
class LocalBroadcaster(
    private val chatService: ChatService
) {
    fun broadcastRaw(roomId: UUID, payload: String) {
        chatService.rooms[roomId]?.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(payload))
                } catch (_: Exception) {
                    chatService.leaveRoom(roomId, session)
                }
            }
        }
    }
}