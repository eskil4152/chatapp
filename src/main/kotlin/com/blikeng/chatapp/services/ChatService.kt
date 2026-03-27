package com.blikeng.chatapp.services

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
import com.blikeng.chatapp.dtos.messaging.SendMessageDTO
import com.blikeng.chatapp.dtos.room.RoomUserDTO
import com.blikeng.chatapp.dtos.websocket.ReceivedMessageDTO
import com.blikeng.chatapp.dtos.websocket.WsChat
import com.blikeng.chatapp.dtos.websocket.WsJoined
import com.blikeng.chatapp.dtos.websocket.WsRoomPresence
import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.errors.InvalidMessageException
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.RoomNotFoundException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

// ==========================
// Handles chat session tracking, room membership, message publishing,
// message history retrieval, Redis-backed pending message loading,
// and real-time room fanout coordination.
// ==========================
@Service
class ChatService (
    private val chatRepository: ChatRepository,
    private val roomRepository: RoomRepository,
    private val userRoomRepository: UserRoomRepository,
    private val encrypt: ChatEncrypt,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
    private val presenceHandler: PresenceHandler,
    meterRegistry: MeterRegistry,
) {
    val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    init {
        meterRegistry.gauge("chat.rooms", rooms) { it.size.toDouble() }
    }

    // ==========================
    // Room membership
    // ==========================
    fun joinRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID
            ?: throw InvalidTokenException()

        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId)) {
            throw RoomNotFoundException()
        }

        val room = roomRepository.findById(roomId).orElseThrow { RoomNotFoundException() }

        val existingSessions = rooms[roomId]?.toSet() ?: emptySet()

        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)

        synchronized(session) {
            if (session.isOpen) {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(
                    WsJoined(roomId = roomId, roomName = room.name, encrypted = room.encrypted)
                )))
                getUsersInRoom(roomId).forEach { member ->
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(
                        WsRoomPresence(roomId = roomId, userId = member.id, online = member.online)
                    )))
                }
            }
        }

        val presencePayload = objectMapper.writeValueAsString(
            WsRoomPresence(roomId = roomId, userId = userId, online = true)
        )
        existingSessions.forEach { existingSession ->
            synchronized(existingSession) {
                if (existingSession.isOpen) {
                    existingSession.sendMessage(TextMessage(presencePayload))
                }
            }
        }
    }

    fun leaveRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID

        rooms[roomId]?.remove(session)

        if (rooms[roomId]?.isEmpty() == true) {
            rooms.remove(roomId)
        }

        if (userId != null) {
            val presencePayload = objectMapper.writeValueAsString(
                WsRoomPresence(roomId = roomId, userId = userId, online = presenceHandler.isUserOnline(userId))
            )
            rooms[roomId]?.forEach { remainingSession ->
                synchronized(remainingSession) {
                    if (remainingSession.isOpen) {
                        remainingSession.sendMessage(TextMessage(presencePayload))
                    }
                }
            }
        }
    }

    fun removeSessionFromRooms(session: WebSocketSession): List<UUID> {
        val affectedRoomIds = rooms
            .filterValues { it.contains(session) }
            .keys
            .toList()

        affectedRoomIds.forEach { roomId ->
            val roomSessions = rooms[roomId] ?: return@forEach
            roomSessions.remove(session)
            if (roomSessions.isEmpty()) {
                rooms.remove(roomId)
            }
        }

        return affectedRoomIds
    }

    fun notifyRoomPresence(roomIds: List<UUID>, userId: UUID, online: Boolean) {
        roomIds.forEach { roomId ->
            val payload = objectMapper.writeValueAsString(
                WsRoomPresence(roomId = roomId, userId = userId, online = online)
            )
            rooms[roomId]?.forEach { session ->
                synchronized(session) {
                    if (session.isOpen) {
                        session.sendMessage(TextMessage(payload))
                    }
                }
            }
        }
    }

    // ==========================
    // Message publishing and fetching
    // ==========================
    fun broadcast(roomId: UUID, message: ReceivedMessageDTO, username: String) {
        val timestamp = Timestamp(System.currentTimeMillis())
        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(message.userId, roomId)) throw RoomNotFoundException()

        if (message.content.isBlank() || message.content.length > 2000) throw InvalidMessageException()

        if (message.type == "MESSAGE") addMessage(message, username)

        val sendMessage = if (message.type == "MESSAGE") {
            WsChat(content = message.content, username = username, type = message.type, timestamp = timestamp)
        } else {
            WsChat(content = message.content, username = "Server", type = message.type, timestamp = timestamp)
        }

        val json = objectMapper.writeValueAsString(sendMessage)

        redisTemplate.convertAndSend("room:${roomId}", json)
    }

    fun addMessage(message: ReceivedMessageDTO, username: String){
        val room = roomRepository.findById(message.roomId).orElseThrow()

        val messageId = UUID.randomUUID()

        val rabbitMessage = if (room.encrypted) {
            val v = room.keyVersion
            val enc = encrypt.encrypt(
                plaintext = message.content,
                aad = configureAad(room.id, messageId, message.userId),
            )

            RabbitMessageDTO(
                id = messageId,
                roomId = room.id,
                userId = message.userId,
                username = username,
                ciphertext = enc.ciphertext,
                nonce = enc.nonce,
                keyVersion = v
            )
        } else {
            RabbitMessageDTO(
                id = messageId,
                username = username,
                roomId = room.id,
                userId = message.userId,
                message = message.content
            )
        }

        val json = objectMapper.writeValueAsString(rabbitMessage)

        rabbitTemplate.convertAndSend("chat.buffer", rabbitMessage)
        redisTemplate.opsForList().rightPush("chat.peek.${message.roomId}", json)
    }

    fun fetchAllMessages(messages: List<SendMessageDTO>, session: WebSocketSession){
        for (m in messages) {
            val content = if (m.ciphertext == null) {
                m.message ?: ""
            } else {
                val nonce = m.nonce ?: throw InvalidMessageException()

                encrypt.decrypt(
                    ciphertext = m.ciphertext,
                    nonce = nonce,
                    aad = configureAad(m.roomId, m.id, m.userId),
                )
            }

            synchronized(session) {
                if (session.isOpen) {
                    session.sendMessage(
                        TextMessage(
                            objectMapper.writeValueAsString(
                                WsChat(content = content, username = m.username, timestamp = m.timestamp)
                            )
                        )
                    )
                }
            }
        }
    }

    private fun getPendingMessages(roomId: UUID): List<RabbitMessageDTO> {
        return redisTemplate.opsForList()
            .range("chat.peek.$roomId", 0, -1)
            ?.mapNotNull { objectMapper.readValue(it, RabbitMessageDTO::class.java) }
            ?: emptyList()
    }

    fun getRoomMessages(roomId: UUID, page: Int, size: Int): List<SendMessageDTO> {
        if (page < 0 || size !in setOf(25, 50, 100)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size")

        val persisted = chatRepository
            .findByRoomIdOrderByTimestampDesc(
                roomId,
                PageRequest.of(page, size)
            )
            .content
            .map { toSendMessageDTO(it) }

        if (page != 0) {
            return persisted.sortedBy { it.timestamp }
        }

        val buffered = getPendingMessages(roomId).map { message ->
            SendMessageDTO(
                id = message.id,
                roomId = message.roomId,
                userId = message.userId,
                username = message.username,
                message = message.message,
                nonce = message.nonce,
                ciphertext = message.ciphertext,
                timestamp = message.timestamp,
                keyVersion = message.keyVersion
            )
        }

        return (persisted + buffered)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
            .takeLast(size)
    }

    private fun toSendMessageDTO(message: ChatEntity): SendMessageDTO {
        return SendMessageDTO(
            id = message.id,
            roomId = message.roomId,
            userId = message.user.id,
            username = message.user.username,
            message = message.message,
            nonce = message.nonce,
            ciphertext = message.ciphertext,
            timestamp = message.timestamp,
            keyVersion = message.keyVersion
        )
    }

    // ==========================
    // Helper methods
    // ==========================
    fun getUsersInRoom(roomId: UUID): List<RoomUserDTO> {
        return userRoomRepository.findUsersByRoomId(roomId).map {
            RoomUserDTO(
                id = it.id,
                username = it.username,
                avatarUrl = it.avatarUrl,
                online = presenceHandler.isUserOnline(it.id)
            )
        }
    }
}