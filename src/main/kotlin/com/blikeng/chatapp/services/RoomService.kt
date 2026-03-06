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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class RoomService(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRoomRepository: UserRoomRepository,
    @Autowired private val userService: UserService,
) {
    fun makeNewRoom(roomName: String, encrypted: Boolean?) {
        val id = SecurityContextHolder.getContext().authentication?.principal as? UUID ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
        if (roomName.trim().isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_NAME)

        userService.getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val room = roomRepository.save(
            RoomEntity(
                name = roomName,
                encrypted = encrypted == true,
                keyVersion = if (encrypted == true) 1 else null
            ))

        val userRoom = UserRoomEntity(UserRoomId(id, room.id), RoomRole.OWNER)
        userRoomRepository.save(userRoom)
    }

    fun getAllUserRooms(): List<RoomEntity> {
        val id = SecurityContextHolder.getContext().authentication?.principal as? UUID ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
        val rooms = roomRepository.findRoomsForUser(id)

        return rooms
    }

    fun joinRoom(roomId: UUID){
        val id = SecurityContextHolder.getContext().authentication?.principal as? UUID ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
        userService.getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        roomRepository.findById(roomId).orElse(null) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            ROOM_NOT_FOUND
        )

        val userRoom = UserRoomEntity(UserRoomId(id, roomId), RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }
}