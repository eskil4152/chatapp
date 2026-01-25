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
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val json = jacksonObjectMapper().readTree(message.payload)
        val room = json.get("room")

        val users = chatService.getUsersInRoom(room.asInt()) ?: return
        chatService.broadcast(message, users)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        chatService.tempbar(session)
    }
}