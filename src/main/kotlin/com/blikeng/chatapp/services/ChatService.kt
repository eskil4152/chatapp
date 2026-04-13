package com.blikeng.chatapp.services

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
import com.blikeng.chatapp.dtos.messaging.SendMessageDTO
import com.blikeng.chatapp.dtos.room.RoomUserDTO
import com.blikeng.chatapp.dtos.websocket.ReceivedMessage
import com.blikeng.chatapp.dtos.websocket.RoomMember
import com.blikeng.chatapp.dtos.websocket.WsChat
import com.blikeng.chatapp.dtos.websocket.WsRoomJoined
import com.blikeng.chatapp.dtos.websocket.WsRoomPresence
import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.errors.InvalidMessageException
import com.blikeng.chatapp.errors.InvalidParametersException
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.RoomNotFoundException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Instant
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
    private val userService: UserService,
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

        val role = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) ?: throw RoomNotFoundException()

        val room = roomRepository.findById(roomId).orElseThrow { RoomNotFoundException() }

        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)

        synchronized(session) {
            if (session.isOpen) {
                val users = getUsersInRoom(roomId).map { member ->
                    RoomMember(
                        id = member.id,
                        username = member.username,
                        avatar = member.avatarUrl,
                        online = member.online
                    )
                }

                session.sendMessage(TextMessage(objectMapper.writeValueAsString(WsRoomJoined(
                    roomId = roomId,
                    roomName = room.name,
                    members = users,
                    encrypted = room.encrypted,
                    role = role.role,
                ))))
            }
        }
    }

    fun leaveRoom(roomId: UUID, session: WebSocketSession) {
        rooms[roomId]?.remove(session)

        if (rooms[roomId]?.isEmpty() == true) {
            rooms.remove(roomId)
        }
    }

    fun removeSessionFromRooms(session: WebSocketSession) {
        rooms.entries.removeIf { (_, sessions) ->
            sessions.remove(session)
            sessions.isEmpty()
        }
    }

    fun notifyRoomPresence(userId: UUID, online: Boolean) {
        val roomIds = userRoomRepository.findAllIdRoomIdsByIdUserId(userId);

        roomIds.forEach { roomId ->
            val payload = objectMapper.writeValueAsString(
                WsRoomPresence(roomId = roomId, userId = userId, online = online)
            )

            redisTemplate.convertAndSend("room:${roomId}", payload)
        }
    }

    // ==========================
    // Message publishing and fetching
    // ==========================
    fun broadcast(roomId: UUID, message: ReceivedMessage, username: String) {
        val timestamp = Instant.now()
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

    fun addMessage(message: ReceivedMessage, username: String){
        val room = roomRepository.findById(message.roomId).orElseThrow { RoomNotFoundException() }

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

        redisTemplate.opsForList().rightPush("chat.peek.${message.roomId}", json)
        rabbitTemplate.convertAndSend("chat.buffer", rabbitMessage)
    }

    fun getRoomMessages(roomId: UUID, page: Int, size: Int): List<SendMessageDTO> {
        if (page < 0 || size !in setOf(25, 50, 100)) throw InvalidParametersException()

        val room = roomRepository.findById(roomId).orElseThrow { RoomNotFoundException() }
        val persisted = chatRepository
            .findByRoomIdOrderByTimestampDesc(
                roomId,
                PageRequest.of(page, size)
            )
            .content
            .map { toSendMessageDTO(it, room.encrypted) }

        if (page != 0) {
            return persisted.sortedBy { it.timestamp }
        }

        val buffered = if (room.encrypted) {
            getPendingMessages(roomId).map { message ->
                if (message.ciphertext == null || message.nonce == null) throw InvalidMessageException()

                SendMessageDTO(
                    id = message.id,
                    roomId = message.roomId,
                    userId = message.userId,
                    username = message.username,
                    message = encrypt.decrypt(message.ciphertext, message.nonce, aad = configureAad(roomId, message.id, message.userId)),
                    timestamp = message.timestamp,
                )
            }
        } else {
            getPendingMessages(roomId).map { message ->
                SendMessageDTO(
                    id = message.id,
                    roomId = message.roomId,
                    userId = message.userId,
                    username = message.username,
                    message = message.message,
                    timestamp = message.timestamp,
                )
            }
        }

        return (persisted + buffered)
            .distinctBy { it.id }
            .sortedBy { it.timestamp }
            .takeLast(size)
    }

    private fun toSendMessageDTO(message: ChatEntity, encrypted: Boolean): SendMessageDTO {
        if (encrypted) {
            val cipher = message.ciphertext ?: throw InvalidMessageException()
            val nonce = message.nonce ?: throw InvalidMessageException()

            return SendMessageDTO(
                id = message.id,
                roomId = message.roomId,
                userId = message.user.id,
                username = message.user.username,
                message = encrypt.decrypt(cipher, nonce, aad = configureAad(message.roomId, message.id, message.user.id)),
                timestamp = message.timestamp,
            )
        }

        return SendMessageDTO(
            id = message.id,
            roomId = message.roomId,
            userId = message.user.id,
            username = message.user.username,
            message = message.message,
            timestamp = message.timestamp,
        )
    }

    // ==========================
    // Helper methods
    // ==========================
    private fun getPendingMessages(roomId: UUID): List<RabbitMessageDTO> {
        return redisTemplate.opsForList()
            .range("chat.peek.$roomId", 0, -1)
            ?.mapNotNull { objectMapper.readValue(it, RabbitMessageDTO::class.java) }
            ?: emptyList()
    }

    fun getUsersInRoom(roomId: UUID): List<RoomUserDTO> {
        val userRooms = userRoomRepository.findUserRoomsByRoomId(roomId).associateBy { it.id.userId }
        val users = userService.getAllById(userRooms.keys.toList())

        return users.map {
            RoomUserDTO(
                id = it.id,
                username = it.username,
                avatarUrl = it.avatarUrl,
                online = presenceHandler.isUserOnline(it.id),
                role = userRooms[it.id]?.role,
            )
        }
    }
}