package com.blikeng.chatapp.websocker

import com.blikeng.chatapp.services.ChatService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class ChatWebSockerHandler(private val chatService: ChatService) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        chatService.addSession(session)
        chatService.broadcast(TextMessage("User connected"), session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        chatService.broadcast(message, session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        chatService.removeSession(session)
        chatService.broadcast(TextMessage("User left"), session)
    }
}