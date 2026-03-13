package com.blikeng.chatapp.services

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.dtos.*
import com.blikeng.chatapp.dtos.websocket.RabbitMessageDTO
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.RoomNotFoundException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
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
) {
    val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    val users = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    // ==========================
    // Session handling
    // ==========================
    fun registerSession(userId: UUID, session: WebSocketSession) {
        users.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
        presenceHandler.userConnected(userId)
    }

    fun removeSession(userId: UUID, session: WebSocketSession) {
        users[userId]?.remove(session)
        if (users[userId]?.isEmpty() == true) {
            users.remove(userId)
        }

        val affectedRoomIds = rooms
            .filterValues { it.contains(session) }
            .keys
            .toList()

        affectedRoomIds.forEach { roomId ->
            rooms[roomId]?.remove(session)

            val stillPresentInRoom = rooms[roomId]
                ?.any { it.attributes["userId"] == userId } == true

            if (!stillPresentInRoom) {
                presenceHandler.userLeftRoom(roomId, userId)
            }

            if (rooms[roomId]?.isEmpty() == true) {
                rooms.remove(roomId)
            }
        }

        presenceHandler.userDisconnected(userId)
    }

    // ==========================
    // Room membership
    // ==========================
    fun leaveRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID

        rooms[roomId]?.remove(session)

        if (userId != null) {
            val stillPresentInRoom = rooms[roomId]
                ?.any { (it.attributes["userId"] as? UUID) == userId } == true

            if (!stillPresentInRoom) {
                presenceHandler.userLeftRoom(roomId, userId)
            }
        }

        if (rooms[roomId]?.isEmpty() == true) {
            rooms.remove(roomId)
        }
    }

    fun joinRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID
            ?: throw InvalidTokenException()

        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId)) {
            throw RoomNotFoundException()
        }

        val room = roomRepository.findById(roomId).orElseThrow()

        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)
        presenceHandler.userJoinedRoom(roomId, userId)

        session.sendMessage(
            TextMessage(
                objectMapper.writeValueAsString(
                    WsJoined(
                        roomId = roomId,
                        roomName = room.name,
                        encrypted = room.encrypted
                    )
                )
            )
        )

        val persisted = chatRepository.getAllChatsByRoomId(roomId).map { message ->
            SendMessageDTO(
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

        val allMessages = (persisted + buffered).sortedBy { it.timestamp }
        fetchAllMessages(allMessages, session)
    }

    // ==========================
    // Message publishing and fetching
    // ==========================
    fun broadcast(roomId: UUID, message: ReceivedMessageDTO, username: String) {
        val timestamp = Timestamp(System.currentTimeMillis())
        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(message.userId, roomId)) throw RoomNotFoundException()

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
                keyVersion = v!!
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
                encrypt.decrypt(
                    ciphertext = m.ciphertext,
                    nonce = m.nonce!!,
                    aad = configureAad(m.roomId, m.id, m.userId),
                    keyVersion = m.keyVersion!!
                )
            }

            session.sendMessage(
                TextMessage(
                    objectMapper.writeValueAsString(
                        WsChat(content = content, username = m.username, timestamp = m.timestamp)
                    )
                )
            )
        }
    }

    private fun getPendingMessages(roomId: UUID): List<RabbitMessageDTO> {
        return redisTemplate.opsForList()
            .range("chat.peek.$roomId", 0, -1)
            ?.mapNotNull { objectMapper.readValue(it, RabbitMessageDTO::class.java) }
            ?: emptyList()
    }
}

