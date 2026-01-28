package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.services.ChatService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

@Component
class ChatWebSocketHandler(private val chatService: ChatService, private val jsonMapper: JsonMapper) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val id: UUID = (session.attributes["userId"] ?: return) as UUID
        val user: String = (session.attributes["username"] ?: return) as String

        chatService.registerSession(id, session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val json = jacksonObjectMapper().readTree(message.payload)

        val type = json["type"].asString()
        val user = session.attributes["username"] as String

        when (type) {
            "JOIN" -> {
                val roomId = UUID.fromString(json["roomId"].asString())
                chatService.joinRoom(roomId, session)
            }
            "MESSAGE" -> {
                val roomId = UUID.fromString(json["roomId"].asString())

                val payload = jacksonObjectMapper().createObjectNode()
                    .put(type, "MESSAGE")
                    .put("message", json["message"].asString())
                    .put("username", user)

                chatService.broadcast(roomId, TextMessage(payload.toString()))
            }
            "LEAVE" -> {
                val roomId = UUID.fromString(json["roomId"].asString())
                chatService.leaveRoom(roomId, session)
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val id: UUID = (session.attributes["userId"] ?: return) as UUID
        chatService.removeSession(id, session)
    }
}