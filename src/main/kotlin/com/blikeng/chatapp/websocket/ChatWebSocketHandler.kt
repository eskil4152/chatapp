package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.services.ChatService
import org.apache.catalina.User
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.module.kotlin.jacksonObjectMapper

@Component
class ChatWebSocketHandler(private val chatService: ChatService) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        chatService.tempfoo(session)

        val user = session.attributes["userId"] as String
        print("User: $user")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val json = jacksonObjectMapper().readTree(message.payload)
        val type = json.get("type")

        chatService.broadcast(message, session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {

    }
}