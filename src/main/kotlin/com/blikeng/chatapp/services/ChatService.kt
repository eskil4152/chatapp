package com.blikeng.chatapp.services

import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service
class ChatService {
    val rooms = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()
    val users = ConcurrentHashMap<UUID, WebSocketSession>()

    fun registerSession(userId: UUID, session: WebSocketSession) {
        users[userId] = session
    }

    fun removeSession(userId: UUID, session: WebSocketSession){
        users.remove(userId)
        rooms.values.forEach { it.remove(session) }
    }

    fun joinRoom(roomId: UUID, session: WebSocketSession){
        rooms.computeIfAbsent(roomId) { CopyOnWriteArraySet() }.add(session)
    }

    fun leaveRoom(roomId: UUID, session: WebSocketSession){
        rooms[roomId]?.remove(session)
    }

    fun broadcast(roomId: UUID, message: TextMessage) {
        rooms[roomId]?.forEach { it.sendMessage(message) }
    }
}