package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.ChatEncrypt
import com.blikeng.chatapp.security.aad
import jakarta.annotation.PreDestroy
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
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

        val ts = Timestamp(System.currentTimeMillis())

        val entity = ChatEntity(
            user = user,
            room = room,
            message = null,
            timestamp = ts
        )

        if (!room.encrypted) {
            entity.message = message.content
            entity.ciphertext = null
            entity.nonce = null
            entity.keyVersion = null
        } else {
            val v = room.keyVersion
            val enc = encrypt.encrypt(
                plaintext = message.content,
                aad = aad(room.id, entity.id, user.id),
                keyVersion = v!!
            )
            entity.message = null
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

    fun joinRoom(roomId: UUID, session: WebSocketSession){
        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)

        val persisted = chatRepository.getAllChatsByRoomId(roomId)
        val buffer = buffer
            .asSequence()
            .filter { it.room.id == roomId }
            .toList()

        val allMessages = (persisted + buffer)
            .sortedBy { it.timestamp }

        println("Sending: ${allMessages.size} messages")
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
                    aad = aad(m.room.id, m.id, m.user.id),
                    keyVersion = m.keyVersion!!
                )
            }

            session.sendMessage(
                TextMessage(
                    jacksonObjectMapper().writeValueAsString(
                        SendMessage(m.user.username, content)
                    )
                )
            )
        }
    }

    fun leaveRoom(roomId: UUID, session: WebSocketSession){
        rooms[roomId]?.remove(session)
    }

    fun broadcast(roomId: UUID, message: ReceivedMessage, username: String) {
        if (message.type == "MESSAGE" && rooms[roomId] != null) addMessage(message)

        val sendMessage = SendMessage(username, message.content)
        rooms[roomId]?.forEach { it.sendMessage(TextMessage(jacksonObjectMapper().writeValueAsString(sendMessage))) }
    }
}

data class ReceivedMessage(val roomId: UUID, val userId: UUID, val content: String, val type: String)
data class SendMessage(val username: String, val content: String)