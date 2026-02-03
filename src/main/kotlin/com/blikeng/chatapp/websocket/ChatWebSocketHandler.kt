package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.services.ChatService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*

@Component
class ChatWebSocketHandler(private val chatService: ChatService) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val id: UUID = (session.attributes["userId"]
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No userID found")) as UUID

        chatService.registerSession(id, session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val json = jacksonObjectMapper().readTree(message.payload)

        val typeString = json["type"].asString()
        val type = try {
            MessageType.valueOf(typeString)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message type")
        }

        val user = (session.attributes["username"] ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No username found")) as String

        when (type) {
            MessageType.JOIN -> {
                val roomId = UUID.fromString(json["roomId"].asString())
                chatService.joinRoom(roomId, session)

                val payload = jacksonObjectMapper().createObjectNode()
                    .put("type", "MESSAGE")
                    .put("username", user)
                    .put("message", "$user entered the room")

                chatService.broadcast(roomId, TextMessage(payload.toString()))
            }
            MessageType.MESSAGE -> {
                val roomId = UUID.fromString(json["roomId"].asString())

                val payload = jacksonObjectMapper().createObjectNode()
                    .put("type", "MESSAGE")
                    .put("message", json["message"].asString())
                    .put("username", user)

                chatService.broadcast(roomId, TextMessage(payload.toString()))
            }
            MessageType.LEAVE -> {
                val roomId = UUID.fromString(json["roomId"].asString())
                chatService.leaveRoom(roomId, session)

                val payload = jacksonObjectMapper().createObjectNode()
                    .put("type", "MESSAGE")
                    .put("username", user)
                    .put("message", "$user left the room")

                chatService.broadcast(roomId, TextMessage(payload.toString()))
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val id: UUID = (session.attributes["userId"] ?:
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No userID found")) as UUID
        chatService.removeSession(id, session)
    }

    enum class MessageType {
        MESSAGE, JOIN, LEAVE
    }
}