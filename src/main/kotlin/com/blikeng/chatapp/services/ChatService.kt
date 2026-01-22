package com.blikeng.chatapp.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service
class ChatService (@Autowired private val roomService: RoomService) {
    private val userSessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    fun tempfoo(session: WebSocketSession) = sessions.add(session)

    fun addSession(session: WebSocketSession) {
        //roomService.addToRoom(session, roomId)
    }

    fun removeSession(roomId: Int, session: WebSocketSession){
        roomService.removeFromRoom(session, roomId)
    }

    fun broadcast(message: TextMessage, sender: WebSocketSession) {
        for (session in sessions) {
            session.sendMessage(message)
        }
    }
}