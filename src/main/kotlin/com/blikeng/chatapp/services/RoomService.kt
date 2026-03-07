package com.blikeng.chatapp.services

import com.blikeng.chatapp.ErrorMessages.INVALID_ROOM_ID
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
    fun makeNewRoom(roomName: String?, encrypted: Boolean?) {
        val userId = getId()
        userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        if (roomName == null || roomName.trim().isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_NAME)

        val room = roomRepository.save(
            RoomEntity(
                name = roomName,
                encrypted = encrypted == true,
                keyVersion = if (encrypted == true) 1 else null
            ))

        val userRoom = UserRoomEntity(UserRoomId(userId, room.id), RoomRole.OWNER)
        userRoomRepository.save(userRoom)
    }

    fun getAllUserRooms(): List<RoomDTO> {
        val userId = getId()
        userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val joinedRooms = roomRepository.findRoomsForUser(userId)

        val roomDtos = joinedRooms.map { room ->
            RoomDTO(roomId = room.room.id.toString(), roomName = room.room.name, encrypted = room.room.encrypted, role = room.role)
        }

        return roomDtos
    }

    fun joinRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_ID)
        }

        roomRepository.findById(roomUUID).orElse(null) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            ROOM_NOT_FOUND
        )

        val userRoom = UserRoomEntity(UserRoomId(userId, roomUUID), RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }

    fun editRoom(roomDTO: RoomDTO) {
        val userId = getId()
        userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val roomId = try {
            UUID.fromString(roomDTO.roomId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid room id")
        }

        val name = roomDTO.roomName
        if (name.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_NAME)
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND)

        if (userRoom.role != RoomRole.OWNER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot edit")
        }

        val room = roomRepository.findById(roomId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND)

        room.name = name
        roomRepository.save(room)
    }

    @Transactional
    fun leaveRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_ID)
        }

        val existed = userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomUUID)
        if (!existed) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND)
        }

        userRoomRepository.deleteByIdUserIdAndIdRoomId(userId, roomUUID)
    }

    @Transactional
    fun deleteRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_ROOM_ID)
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomUUID)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, ROOM_NOT_FOUND)

        if (userRoom.role != RoomRole.OWNER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot delete")
        }

        userRoomRepository.deleteAllByIdRoomId(roomUUID)
        roomRepository.deleteById(roomUUID)
    }
}