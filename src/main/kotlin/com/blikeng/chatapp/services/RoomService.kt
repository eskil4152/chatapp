package com.blikeng.chatapp.services

import com.blikeng.chatapp.ErrorMessages.INVALID_ROOM_NAME
import com.blikeng.chatapp.ErrorMessages.INVALID_USER
import com.blikeng.chatapp.ErrorMessages.ROOM_NOT_FOUND
import com.blikeng.chatapp.getId
import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class RoomService(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRoomRepository: UserRoomRepository,
    @Autowired private val userService: UserService,
) {
    fun makeNewRoom(roomName: String, encrypted: Boolean?) {
        val id = getId()
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

    fun getAllUserRooms(): List<RoomDTO> {
        val id = getId()
        val joinedRooms = roomRepository.findRoomsForUser(id)

        val roomDtos = joinedRooms.map { room ->
            RoomDTO(roomId = room.room.id.toString(), roomName = room.room.name, encrypted = room.room.encrypted, role = room.role)
        }

        return roomDtos
    }

    fun joinRoom(roomId: String){
        val id = getId()
        userService.getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_NAME)
        }

        roomRepository.findById(roomUUID).orElse(null) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            ROOM_NOT_FOUND
        )

        val userRoom = UserRoomEntity(UserRoomId(id, roomUUID), RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }

    @Transactional
    fun leaveRoom(roomId: String?){
        val id = getId()
        userService.getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_NAME)
        }

        val existed = userRoomRepository.existsByIdUserIdAndIdRoomId(id, roomUUID)
        if (!existed) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND)
        }

        userRoomRepository.deleteByIdUserIdAndIdRoomId(id, roomUUID)
    }
}