package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import jakarta.annotation.PreDestroy
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
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
import java.util.concurrent.atomic.AtomicInteger

@Service
@EnableScheduling
class ChatService(
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRepository: UserRepository,
    private val chatFlushService: ChatFlushService,
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
        val chatEntity = ChatEntity(
            user = userRepository.findById(message.userId).orElseThrow(),
            room = roomRepository.findById(message.roomId).orElseThrow(),
            message = message.content,
            timestamp = Timestamp(System.currentTimeMillis())
        )

        buffer.add(chatEntity)
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

        fetchAllMessages(allMessages, session)
    }

    fun fetchAllMessages(messages: List<ChatEntity>, session: WebSocketSession){
        for (message in messages) {
            session.sendMessage(TextMessage(jacksonObjectMapper().writeValueAsString(SendMessage(message.user.username, message.message!!))))
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