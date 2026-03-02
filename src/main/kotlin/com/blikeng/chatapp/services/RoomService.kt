package com.blikeng.chatapp.services

import com.blikeng.chatapp.ErrorMessages.INVALID_ROOM_NAME
import com.blikeng.chatapp.ErrorMessages.INVALID_TOKEN
import com.blikeng.chatapp.ErrorMessages.INVALID_USER
import com.blikeng.chatapp.ErrorMessages.ROOM_NOT_FOUND
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class RoomService(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRoomRepository: UserRoomRepository,
    @Autowired private val userService: UserService,
    @Autowired private val jwtService: JwtService
) {
    fun makeNewRoom(roomName: String, encrypted: Boolean?, token: String) {
        if (roomName.trim().isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_NAME)

        val (_, userId) =
            jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)

        val user =
            userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val room = roomRepository.save(
            RoomEntity(
                name = roomName,
                encrypted = encrypted == true,
                keyVersion = if (encrypted == true) 1 else null
            ))

        val id = UserRoomId(userId, room.id)
        val userRoom = UserRoomEntity(id, user, room, RoomRole.OWNER)

        userRoomRepository.save(userRoom)
    }

    fun getAllUserRooms(token: String): List<RoomEntity> {
        val (_, userId ) =
            jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)

        val rooms = userRoomRepository.findAllRoomsByUserId(userId)

        return rooms
    }

    fun joinRoom(roomId: UUID, token: String){
        val (_, userId ) =
            jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)

        val user =
            userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val room = roomRepository.findById(roomId).orElse(null) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            ROOM_NOT_FOUND
        )

        val userRoom = UserRoomEntity(UserRoomId(userId, roomId), user, room, RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }
}