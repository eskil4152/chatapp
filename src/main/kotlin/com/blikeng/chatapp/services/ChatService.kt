package com.blikeng.chatapp.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service
class ChatService (@Autowired private val roomService: RoomService) {
    private val rooms = HashMap<Int, MutableSet<WebSocketSession>>()
        .apply {
            put(1, CopyOnWriteArraySet())
            put(2, CopyOnWriteArraySet())
            put(3, CopyOnWriteArraySet())
        }

    fun tempfoo(session: WebSocketSession){
        for (room in rooms) {
            room.value.add(session)
        }
    }

    fun tempbar(session: WebSocketSession){
        for (room in rooms) {
            room.value.remove(session)
        }
    }

    fun getUsersInRoom(roomId: Int): List<WebSocketSession>? {
        return rooms[roomId]?.toList()
    }

    fun addSession(session: WebSocketSession) {
        //roomService.addToRoom(session, roomId)
    }

    fun removeSession(roomId: Int, session: WebSocketSession){
        roomService.removeFromRoom(session, roomId)
    }

    fun broadcast(message: TextMessage, recipients: List<WebSocketSession>) {
        for (recipient in recipients) {
            recipient.sendMessage(message)
            print("MESSAGE SENT")
        }
    }
}