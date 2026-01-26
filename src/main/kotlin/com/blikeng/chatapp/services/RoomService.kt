package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class RoomService(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRoomRepository: UserRoomRepository,
    @Autowired private val userService: UserService,
    @Autowired private val jwtService: JwtService
) {
    val roomSessions = ConcurrentHashMap<Int, MutableSet<WebSocketSession>>()

    fun addToRoom(session: WebSocketSession, roomId: Int) {
        roomSessions[roomId]?.add(session)
    }

    fun removeFromRoom(session: WebSocketSession, roomId: Int) {
        roomSessions[roomId]?.remove(session)
    }

    fun makeNewRoom(roomName: String, token: String): RoomEntity? {
        val userId = jwtService.validateToken(token)
        if (userId == null) return null

        val user = userService.getUserById(userId)
        if (user == null) return null

        val room = roomRepository.save(RoomEntity(name = roomName))
        val room_id = room.id
        if (room_id == null) return null

        val id = UserRoomId(userId, room_id)

        val userRoom = UserRoomEntity(id, user, room, RoomRole.OWNER)
        userRoomRepository.save(userRoom)

        return room
    }

    fun getAllUserRooms(token: String): List<RoomEntity>? {
        val userId = jwtService.validateToken(token)
        if (userId == null) return null

        val rooms = userRoomRepository.findAllRoomsByUserId(userId)
        if (rooms.isEmpty()) return null

        println(rooms[0].id)
        println(rooms[0].name)

        return rooms
    }

    fun joinRoom(roomId: UUID, token: String){
        val userId = jwtService.validateToken(token)
        if (userId == null) return

        val user = userService.getUserById(userId)
        if (user == null) return

        val room = roomRepository.findById(roomId).orElse(null)
        if (room == null) return

        val userRoom = UserRoomEntity(UserRoomId(userId, roomId), user, room, RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }
}