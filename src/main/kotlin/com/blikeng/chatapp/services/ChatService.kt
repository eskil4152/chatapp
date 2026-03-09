package com.blikeng.chatapp.services

import com.blikeng.chatapp.ErrorMessages.INVALID_TOKEN
import com.blikeng.chatapp.ErrorMessages.NOT_PERMITTED
import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.dtos.WsChat
import com.blikeng.chatapp.dtos.WsJoined
import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.ChatEncrypt
import jakarta.annotation.PreDestroy
import org.flywaydb.core.extensibility.Tier
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

@Service
@EnableScheduling
class ChatService(
    private val chatRepository: ChatRepository,
    private val roomRepository: RoomRepository,
    private val userRepository: UserRepository,
    private val userRoomRepository: UserRoomRepository,
    private val chatFlushService: ChatFlushService,
    private val encrypt: ChatEncrypt
) {
    val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    val users = ConcurrentHashMap<UUID, WebSocketSession>()

    private val buffer = ConcurrentLinkedQueue<ChatEntity>()
    private val flushing = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${chat.flush.fixedDelayMs:120000}")
    fun scheduledFlush() {
        flushNow()
    }

    @PreDestroy
    fun shutdownFlush() {
        flushNow()
    }

    private fun flushNow() {
        if (!flushing.compareAndSet(false, true)) return
        try {
            val batch = ArrayList<ChatEntity>(1024)
            while (true) {
                val item = buffer.poll() ?: break
                batch.add(item)
            }
            if (batch.isNotEmpty()) chatFlushService.saveBatch(batch)
        } finally {
            flushing.set(false)
        }
    }

    fun addMessage(message: ReceivedMessage){
        val user = userRepository.findById(message.userId).orElseThrow()
        val room = roomRepository.findById(message.roomId).orElseThrow()

        val entity = ChatEntity(
            user = user,
            roomId = room.id,
        )

        if (!room.encrypted) {
            entity.message = message.content
        } else {
            val v = room.keyVersion
            val enc = encrypt.encrypt(
                plaintext = message.content,
                aad = configureAad(room.id, entity.id, user.id),
                keyVersion = v!!
            )

            entity.ciphertext = enc.ciphertext
            entity.nonce = enc.nonce
            entity.keyVersion = v
        }

        buffer.add(entity)
    }

    fun registerSession(userId: UUID, session: WebSocketSession) {
        users[userId] = session
    }

    fun removeSession(userId: UUID, session: WebSocketSession){
        users.remove(userId)
        rooms.values.forEach { it.remove(session) }
    }

    fun joinRoom(roomId: UUID, session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)

        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, NOT_PERMITTED)
        }

        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)

        val room = roomRepository.findById(roomId).orElseThrow()
        session.sendMessage(
            TextMessage(
                jacksonObjectMapper().writeValueAsString(
                    WsJoined(
                        roomId = roomId,
                        roomName = room.name,
                        encrypted = room.encrypted
                    )
                )
            )
        )

        val persisted = chatRepository.getAllChatsByRoomId(roomId)
        val buffered = buffer.asSequence().filter { it.roomId == roomId }.toList()

        val allMessages = (persisted + buffered).sortedBy { it.timestamp }
        fetchAllMessages(allMessages, session)
    }

    fun fetchAllMessages(messages: List<ChatEntity>, session: WebSocketSession){
        for (m in messages) {
            val content = if (m.ciphertext == null) {
                m.message ?: ""
            } else {
                encrypt.decrypt(
                    ciphertext = m.ciphertext!!,
                    nonce = m.nonce!!,
                    aad = configureAad(m.roomId, m.id, m.user.id),
                    keyVersion = m.keyVersion!!
                )
            }

            session.sendMessage(
                TextMessage(
                    jacksonObjectMapper().writeValueAsString(
                        WsChat(content = content, username = m.user.username, timestamp = m.timestamp)
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
        if (message.type == "MESSAGE" && rooms[roomId] != null) addMessage(message)

        if (!userRoomRepository.existsByIdUserIdAndIdRoomId(message.userId, roomId)) throw ResponseStatusException(HttpStatus.FORBIDDEN, NOT_PERMITTED);

        val sendMessage = if (message.type == "MESSAGE") {
            WsChat(content = message.content, username = username, type = message.type, timestamp = timestamp)
        } else {
            WsChat(content = message.content, username = "Server", type = message.type, timestamp = timestamp)
        }

        val json = jacksonObjectMapper().writeValueAsString(sendMessage)
        rooms[roomId]?.forEach { it.sendMessage(TextMessage(json)) }
    }
}

data class ReceivedMessage(val roomId: UUID, val userId: UUID, val content: String, val type: String)