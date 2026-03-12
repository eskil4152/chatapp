package com.blikeng.chatapp.services

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.dtos.WsChat
import com.blikeng.chatapp.dtos.WsJoined
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.RoomNotFoundException
import com.blikeng.chatapp.buffer.RabbitMessage
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.ChatEncrypt
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

@Service
class ChatService (
    private val chatRepository: ChatRepository,
    private val roomRepository: RoomRepository,
    private val userRoomRepository: UserRoomRepository,
    private val encrypt: ChatEncrypt,
    private val redisTemplate: RedisTemplate<String, String>,
    private val rabbitTemplate: RabbitTemplate,
    private val objectMapper: ObjectMapper,
) {
    val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    val users = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    fun addMessage(message: ReceivedMessage, username: String){
        val room = roomRepository.findById(message.roomId).orElseThrow()

        val messageId = UUID.randomUUID()

        val rabbitMessage = if (room.encrypted) {
            val v = room.keyVersion
            val enc = encrypt.encrypt(
                plaintext = message.content,
                aad = configureAad(room.id, messageId, message.userId),
                keyVersion = v!!
            )

            RabbitMessage(
                id = messageId,
                roomId = room.id,
                userId = message.userId,
                username = username,
                ciphertext = enc.ciphertext,
                nonce = enc.nonce,
                keyVersion = v
            )
        } else {
            RabbitMessage(
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

    fun registerSession(userId: UUID, session: WebSocketSession) {
        users.computeIfAbsent(userId) { CopyOnWriteArraySet() }.add(session)
    }

    fun removeSession(userId: UUID, session: WebSocketSession) {
        users[userId]?.remove(session)
        if (users[userId]?.isEmpty() == true) {
            users.remove(userId)
        }
    }

    fun joinRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID
            ?: throw InvalidTokenException()

        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId)) {
            throw RoomNotFoundException()
        }

        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)

        val room = roomRepository.findById(roomId).orElseThrow()
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
            SendMessage(
                id = message.id,
                roomId = message.roomId,
                userId = message.user.id,
                username = message.user.username,
                message = message.message,
                nonce = message.nonce,
                ciphertext = message.ciphertext,
                timestamp = message.timestamp
            )
        }

        val buffered = getPendingMessages(roomId).map { message ->
            SendMessage(
                id = message.id,
                roomId = message.roomId,
                userId = message.userId,
                username = message.username,
                message = message.message,
                nonce = message.nonce,
                ciphertext = message.ciphertext,
                timestamp = message.timestamp)
        }

        val allMessages = (persisted + buffered).sortedBy { it.timestamp }
        fetchAllMessages(allMessages, session)
    }

    fun fetchAllMessages(messages: List<SendMessage>, session: WebSocketSession){
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

    fun leaveRoom(roomId: UUID, session: WebSocketSession){
        rooms[roomId]?.remove(session)
    }

    fun broadcast(roomId: UUID, message: ReceivedMessage, username: String) {
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

    private fun getPendingMessages(roomId: UUID): List<RabbitMessage> {
        return redisTemplate.opsForList()
            .range("chat.peek.$roomId", 0, -1)
            ?.mapNotNull { objectMapper.readValue(it, RabbitMessage::class.java) }
            ?: emptyList()
    }
}

data class ReceivedMessage(val roomId: UUID, val userId: UUID, val content: String, val type: String)

data class SendMessage(
    val id: UUID,
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val message: String?,
    val nonce: ByteArray?,
    val ciphertext: ByteArray?,
    val timestamp: Timestamp,
    val keyVersion: Int? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SendMessage

        if (username != other.username) return false
        if (message != other.message) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (nonce?.contentHashCode() ?: 0)
        result = 31 * result + (ciphertext?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}