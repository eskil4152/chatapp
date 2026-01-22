package com.blikeng.chatapp.services

import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class RoomService {
    val roomSessions = ConcurrentHashMap<Int, MutableSet<WebSocketSession>>()

    fun addToRoom(session: WebSocketSession, roomId: Int) {
        roomSessions[roomId]?.add(session)
    }

    fun removeFromRoom(session: WebSocketSession, roomId: Int) {
        roomSessions[roomId]?.remove(session)
    }

    fun getSessions(roomId: Int): List<WebSocketSession>? {
        val res: MutableSet<WebSocketSession> ?= roomSessions[roomId]
        return res?.toList()
    }

    fun checkRoomForMember(){

    }

    fun addMessageToLogs(){

    }
}