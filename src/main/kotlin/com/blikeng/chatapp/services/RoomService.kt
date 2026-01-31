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
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
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
    fun makeNewRoom(roomName: String, token: String): RoomEntity? {
        if (roomName.trim() == "") throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid name")

        val ( username, userId ) =
            jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")

        val user =
            userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user")

        val room = roomRepository.save(RoomEntity(name = roomName))

        val id = UserRoomId(userId, room.id!!)

        val userRoom = UserRoomEntity(id, user, room, RoomRole.OWNER)
        userRoomRepository.save(userRoom)

        return room
    }

    fun getAllUserRooms(token: String): List<RoomEntity>? {
        val ( username, userId ) =
            jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")

        val rooms = userRoomRepository.findAllRoomsByUserId(userId)

        return rooms
    }

    fun joinRoom(roomId: UUID, token: String){
        val ( username, userId ) =
            jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")

        val user =
            userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user")

        val room = roomRepository.findById(roomId).orElse(null) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Room not found"
        )

        val userRoom = UserRoomEntity(UserRoomId(userId, roomId), user, room, RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }
}