package com.blikeng.chatapp.websocket

import com.blikeng.chatapp.dtos.websocket.ReceivedMessage
import com.blikeng.chatapp.dtos.websocket.WsError
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.security.ratelimit.WsRateLimitService
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.FriendService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.*
import java.util.concurrent.ConcurrentHashMap


// ==========================
// Handles authenticated WebSocket chat connections.
// Registers and removes user sessions, routes incoming WebSocket messages
// to chat room actions, and sends structured WebSocket error responses
// when message handling fails.
// ==========================
@Component
class ChatWebSocketHandler(
    private val chatService: ChatService,
    private val objectMapper: ObjectMapper,
    private val wsRateLimitService: WsRateLimitService,
    private val sessionRegistry: SessionRegistry,
    @Value("\${chat.ping.ttlMs}") private val ttlMs: Long
) : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val lastPing = ConcurrentHashMap<String, Long>()

    // ==========================
    // Connection lifecycle
    // ==========================
    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = getUserId(session)
        sessionRegistry.registerSession(userId, session)
        lastPing[session.id] = System.currentTimeMillis()
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = getUserId(session)

        lastPing.remove(session.id)
        sessionRegistry.removeSession(userId, session)
    }

    // ==========================
    // Message handling
    // ==========================
    override fun handleTextMessage(session: WebSocketSession, wsMessage: TextMessage) {
        try {
            val json = objectMapper.readTree(wsMessage.payload)
            val type = parseMessageType(json)

            when (type) {
                MessageType.JOIN -> handleJoin(session, json)
                MessageType.MESSAGE -> handleChatMessage(session, json)
                MessageType.LEAVE -> handleLeave(session, json)
                MessageType.PING -> {
                    lastPing[session.id] = System.currentTimeMillis()
                    session.sendMessage(TextMessage("pong"))
                }
                MessageType.SYNC -> sessionRegistry.sendSnapshots(getUserId(session), session)
            }
        } catch (e: Exception) {
            sendWsError(session, e)
        }
    }

    enum class MessageType {
        MESSAGE, JOIN, LEAVE, PING, SYNC
    }

    // ==========================
    // Shutdown
    // ==========================
    @PreDestroy
    fun shutdown() {
        sessionRegistry.users.values.flatten().forEach { session ->
            try {
                session.close(CloseStatus.GOING_AWAY)
            } catch (e: Exception) {
                logger.error("Failed to close session {} on shutdown", session.id, e)
            }
        }
    }

    // ==========================
    // Internal helpers
    // ==========================
    @Scheduled(fixedDelayString = "\${chat.ping.sweepDelayMs}")
    fun sweepStaleSessions() {
        val cutoff = System.currentTimeMillis() - ttlMs

        lastPing.forEach { (sessionId, time) ->
            if (time < cutoff) {
                val session = sessionRegistry.getSessionById(sessionId) ?: run {
                    lastPing.remove(sessionId)
                    return@forEach
                }

                try {
                    session.close(CloseStatus.SESSION_NOT_RELIABLE)
                } catch (e: Exception) {
                    logger.error("Failed to close stale session {}", session.id, e)
                }

            }
        }
    }

    private fun handleJoin(session: WebSocketSession, json: JsonNode) {
        val roomId = getRoomId(json)
        val userId = getUserId(session)
        val username = getUsername(session)

        chatService.joinRoom(roomId, session)

        val message = ReceivedMessage(roomId, userId, "$username joined the room", "JOIN")
        chatService.broadcast(roomId, message, username)
    }

    private fun handleChatMessage(session: WebSocketSession, json: JsonNode) {
        val roomId = getRoomId(json)
        val userId = getUserId(session)
        val username = getUsername(session)

        val messageNode = json["message"]
        if (messageNode == null || messageNode.isNull) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing message field")
        }

        val message = ReceivedMessage(
            roomId,
            userId,
            messageNode.asText(),
            "MESSAGE"
        )

        if (!checkRateLimit(userId, session)) return
        chatService.broadcast(roomId, message, username)
    }

    private fun handleLeave(session: WebSocketSession, json: JsonNode) {
        val roomId = getRoomId(json)
        val userId = getUserId(session)
        val username = getUsername(session)

        lastPing.remove(session.id)
        chatService.leaveRoom(roomId, session)
        val message = ReceivedMessage(roomId, userId, "$username left the room", "LEAVE")
        chatService.broadcast(roomId, message, username)
    }

    private fun parseMessageType(json: JsonNode): MessageType {
        val typeString = json["type"].asText()

        return try {
            MessageType.valueOf(typeString)
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid message type")
        }
    }

    private fun getRoomId(json: JsonNode): UUID {
        return try {
            UUID.fromString(json["roomId"].asText())
        } catch (_: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room ID")
        }
    }

    private fun getUserId(session: WebSocketSession): UUID {
        return (session.attributes["userId"]
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No userID found")) as UUID
    }

    private fun getUsername(session: WebSocketSession): String {
        return (session.attributes["username"]
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No username found")) as String
    }

    private fun checkRateLimit(userId: UUID, session: WebSocketSession) : Boolean{
        if (!wsRateLimitService.tryConsumeMessage(userId)) {
            sendWsError(
                session,
                ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded")
            )
            return false
        }

        return true
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
            if (session.isOpen) {
                session.sendMessage(TextMessage(payload))
            }
        }
    }
}