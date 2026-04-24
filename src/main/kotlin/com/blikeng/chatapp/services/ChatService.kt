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
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.dtos.websocket.WsMessageNotification
import com.blikeng.chatapp.dtos.websocket.WsTyping
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Duration
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
    val sessionsInRooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    private val roomNameMap = ConcurrentHashMap<UUID, String>()

    private val userRoomListType = object : TypeReference<List<RoomDTO>>() {}
    private val roomMemberListType = object : TypeReference<List<UUID>>() {}
    private val roomMembersCacheTTL = Duration.ofMinutes(10)

    init {
        meterRegistry.gauge("app.rooms.active", sessionsInRooms) { it.size.toDouble() }
    }

    // ==========================
    // Room membership
    // ==========================
    fun joinRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID
            ?: throw InvalidTokenException()

        val role = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) ?: throw RoomNotFoundException()

        val room = roomRepository.findById(roomId).orElseThrow { RoomNotFoundException() }

        sessionsInRooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)
        roomNameMap[roomId] = room.name

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
        sessionsInRooms[roomId]?.remove(session)

        if (sessionsInRooms[roomId]?.isEmpty() == true) {
            sessionsInRooms.remove(roomId)
        }
    }

    fun removeSessionFromRooms(session: WebSocketSession) {
        sessionsInRooms.entries.removeIf { (_, sessions) ->
            sessions.remove(session)
            sessions.isEmpty()
        }
    }

    fun notifyRoomPresence(userId: UUID, online: Boolean) {
        val cached = redisTemplate.opsForValue()["user:$userId:rooms"]
        val roomIds = if (cached != null) {
            objectMapper.readValue(cached, userRoomListType).map { UUID.fromString(it.roomId) }
        } else {
            userRoomRepository.findAllIdRoomIdsByIdUserId(userId)
        }

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
    fun broadcast(roomId: UUID, userId: UUID, message: ReceivedMessage, username: String) {
        val timestamp = Instant.now()
        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(message.userId, roomId)) throw RoomNotFoundException()

        if (message.content.isBlank() || message.content.length > 2000) throw InvalidMessageException()

        if (message.type == "MESSAGE") addMessage(message, username)

        val sendMessage = WsChat(
            content = message.content,
            userId = userId,
            username = username,
            type = message.type,
            timestamp = timestamp
        )

        val json = objectMapper.writeValueAsString(sendMessage)

        redisTemplate.convertAndSend("room:${roomId}", json)

        val roomMembers = getRoomMemberIds(roomId)
        val roomWideJson = objectMapper.writeValueAsString(
            WsMessageNotification(
                roomId = roomId,
                roomName = roomNameMap[roomId] ?: "Unknown room",
                username = username,
                message = message.content,
            )
        )
        for (memberId in roomMembers) {
            if (presenceHandler.isUserOnline(memberId)) redisTemplate.convertAndSend("user:$memberId", roomWideJson)
        }
    }

    fun addMessage(message: ReceivedMessage, username: String){
        val room = roomRepository.findById(message.roomId).orElseThrow { RoomNotFoundException() }
        roomNameMap[room.id] = room.name

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
                if (message.ciphertext != null && message.nonce != null) {
                    SendMessageDTO(
                        id = message.id,
                        roomId = message.roomId,
                        userId = message.userId,
                        username = message.username,
                        message = encrypt.decrypt(message.ciphertext, message.nonce, aad = configureAad(roomId, message.id, message.userId)),
                        timestamp = message.timestamp,
                    )
                } else if (message.message != null) {
                    SendMessageDTO(
                        id = message.id,
                        roomId = message.roomId,
                        userId = message.userId,
                        username = message.username,
                        message = message.message,
                        timestamp = message.timestamp,
                    )
                } else {
                    throw InvalidMessageException()
                }
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

    fun notifyTyping(roomId: UUID, userId: UUID, username: String) {
        val payload = objectMapper.writeValueAsString(
            WsTyping(
                userId = userId,
                roomId = roomId,
                username = username
            )
        )

        redisTemplate.convertAndSend("room:$roomId", payload)
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
    fun getRoomMemberIds(roomId: UUID): List<UUID> {
        val key = PresenceKeys.roomMembersKey(roomId)
        val cached = redisTemplate.opsForValue()[key]
        if (cached != null) return objectMapper.readValue(cached, roomMemberListType)
        val ids = userRoomRepository.findAllIdUserIdsByIdRoomId(roomId)
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(ids), roomMembersCacheTTL)
        return ids
    }

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