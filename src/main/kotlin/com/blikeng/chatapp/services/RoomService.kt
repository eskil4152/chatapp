package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.errors.*
import com.blikeng.chatapp.tools.getId
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class RoomService(
    @Autowired private val roomRepository: RoomRepository,
    @Autowired private val userRoomRepository: UserRoomRepository,
    @Autowired private val userService: UserService,
) {
    @Autowired
    private lateinit var chatRepository: ChatRepository

    fun makeNewRoom(roomName: String?, encrypted: Boolean?) {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        if (roomName == null || roomName.trim().isEmpty()) throw InvalidRoomNameException()

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
        userService.getUserById(userId) ?: throw InvalidUserException()

        val joinedRooms = roomRepository.findRoomsForUser(userId)

        val roomDtos = joinedRooms.map { room ->
            RoomDTO(roomId = room.room.id.toString(), roomName = room.room.name, encrypted = room.room.encrypted, role = room.role)
        }

        return roomDtos
    }

    fun joinRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        roomRepository.findById(roomUUID).orElse(null) ?: throw RoomNotFoundException()

        val userRoom = UserRoomEntity(UserRoomId(userId, roomUUID), RoomRole.MEMBER)
        userRoomRepository.save(userRoom)
    }

    fun editRoom(roomDTO: RoomDTO) {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomId = try {
            UUID.fromString(roomDTO.roomId)
        } catch (e: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val name = roomDTO.roomName
        if (name.isNullOrBlank()) {
            throw InvalidRoomNameException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (userRoom.role != RoomRole.OWNER) {
            throw NotPermittedException()
        }

        val room = roomRepository.findById(roomId).orElse(null)
            ?: throw RoomNotFoundException()

        room.name = name
        roomRepository.save(room)
    }

    @Transactional
    fun leaveRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val existed = userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomUUID)
        if (!existed) {
            throw RoomNotFoundException()
        }

        userRoomRepository.deleteByIdUserIdAndIdRoomId(userId, roomUUID)
    }

    @Transactional
    fun deleteRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (e: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomUUID)
            ?: throw RoomNotFoundException()

        if (userRoom.role != RoomRole.OWNER) {
            throw NotPermittedException()
        }

        userRoomRepository.deleteAllByIdRoomId(roomUUID)
        roomRepository.deleteById(roomUUID)
    }
}