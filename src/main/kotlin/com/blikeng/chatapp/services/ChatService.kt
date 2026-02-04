package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.sql.Timestamp
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service
class ChatService(
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRepository: UserRepository
) {
    val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    val users = ConcurrentHashMap<UUID, WebSocketSession>()

    fun addMessage(message: ReceivedMessage){
        //messages.computeIfAbsent(roomId) { mutableListOf() }.add()

        val chatEntity = ChatEntity(
            user = userRepository.findById(message.userId).orElseThrow(),
            room = roomRepository.findById(message.roomId).orElseThrow(),
            message = message.content,
            timestamp = Timestamp(System.currentTimeMillis())
        )

        chatRepository.save(chatEntity)
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

        val messages = chatRepository.getAllChatsByRoomId(roomId)

        fetchAllMessages(messages, session)
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
        if (message.type == "MESSAGE") addMessage(message)

        val sendMessage = SendMessage(username, message.content)
        rooms[roomId]?.forEach { it.sendMessage(TextMessage(jacksonObjectMapper().writeValueAsString(sendMessage))) }
    }
}

data class ReceivedMessage(val roomId: UUID, val userId: UUID, val content: String, val type: String)
data class SendMessage(val username: String, val content: String)