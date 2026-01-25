package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.repositories.RoomRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class RoomService(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userService: UserService,
) {
    val roomSessions = ConcurrentHashMap<Int, MutableSet<WebSocketSession>>()

    fun addToRoom(session: WebSocketSession, roomId: Int) {
        roomSessions[roomId]?.add(session)
    }

    fun removeFromRoom(session: WebSocketSession, roomId: Int) {
        roomSessions[roomId]?.remove(session)
    }

    fun makeNewRoom(roomName: String, ownerId: UUID): RoomEntity? {
        val user = userService.getUserById(ownerId)
        if (user == null) return null

        val room = roomRepository.save(RoomEntity(name = roomName, owner = user))

        return room
    }
}