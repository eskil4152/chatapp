package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.dtos.WsError
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.dtos.ReceivedMessageDTO
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*

@Component
class ChatWebSocketHandler(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val id: UUID = (session.attributes["userId"]
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No userID found")) as UUID

        chatService.registerSession(id, session)
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val json = objectMapper.readTree(message.payload)

            val typeString = json["type"].asText()
            val type = try {
                MessageType.valueOf(typeString)
            } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message type")
            }

            val username = (session.attributes["username"] ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No username found")) as String
            val userId = (session.attributes["userId"] ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No User ID found")) as UUID

            when (type) {
                MessageType.JOIN -> {
                    val roomId = UUID.fromString(json["roomId"].asText())
                    chatService.joinRoom(roomId, session)

                    val msg = ReceivedMessageDTO(roomId, userId, "$username joined the room", "JOIN")
                    chatService.broadcast(roomId, msg, username)
                }

                MessageType.MESSAGE -> {
                    val roomId = UUID.fromString(json["roomId"].asText())

                    val message = ReceivedMessageDTO(roomId, userId, json["message"].asText(), "MESSAGE")
                    chatService.broadcast(roomId, message, username)
                }

                MessageType.LEAVE -> {
                    val roomId = UUID.fromString(json["roomId"].asText())
                    chatService.leaveRoom(roomId, session)

                    val message = ReceivedMessageDTO(roomId, userId, "$username left the room", "LEAVE")

                    chatService.broadcast(roomId, message, username)
                }

                MessageType.PING -> Unit
            }
        } catch (e: Exception) {
            sendWsError(session, e)
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val id: UUID = (session.attributes["userId"] ?:
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No userID found")) as UUID
        chatService.removeSession(id, session)
    }

    enum class MessageType {
        MESSAGE, JOIN, LEAVE, PING
    }

    private fun sendWsError(session: WebSocketSession, e: Exception) {
        val (code, msg) = when (e) {
            is ResponseStatusException -> e.statusCode.value() to (e.reason ?: e.message)
            is IllegalArgumentException -> 400 to (e.message ?: "Bad request")
            else -> 500 to "Internal error"
        }

        val payload = objectMapper.writeValueAsString(
            WsError(code = code, message = msg)
        )

        synchronized(session) {
            if (session.isOpen) session.sendMessage(TextMessage(payload))
        }
    }
}