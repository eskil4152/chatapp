package com.blikeng.chatapp.services

import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.CopyOnWriteArraySet

@Service
class ChatService {
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    fun addSession(session: WebSocketSession) = sessions.add(session)
    fun removeSession(session: WebSocketSession) = sessions.remove(session)

    fun broadcast(message: TextMessage, sender: WebSocketSession) {
        for (s in sessions) {
            if (s.isOpen && s != sender) s.sendMessage(message)
        }
    }
}