package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.room.AdministrationDTO
import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.dtos.room.RoomUserDTO
import com.blikeng.chatapp.dtos.room.UnbanDTO
import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.*
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.events.UserRemovedEvent

import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.auth.getId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

// ==========================
// Handles room creation, membership, room updates, room deletion,
// and deterministic private message room creation between friends.
// ==========================
@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val userRoomRepository: UserRoomRepository,
    private val userService: UserService,
    private val friendService: FriendService,
    private val bannedUserService: BannedUserService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    // ==========================
    // Room creation and retrieval
    // ==========================
    fun makeNewRoom(roomName: String?, encrypted: Boolean?) {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        if (roomName == null || roomName.trim().isEmpty()) throw InvalidRoomNameException()

        val room = roomRepository.save(
            RoomEntity(
                name = roomName,
                encrypted = encrypted == true,
                keyVersion = if (encrypted == true) 1 else null,
                type = RoomType.GROUP,
            ))

        val userRoom = UserRoomEntity(UserRoomId(userId, room.id), RoomRole.OWNER, RoomType.GROUP)
        userRoomRepository.save(userRoom)
    }

    fun getAllUserRooms(): List<RoomDTO> {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val joinedRooms = roomRepository.findRoomsForUser(userId)

        val roomDTOs = joinedRooms.map { room ->
            if (room.type == RoomType.PRIVATE) {
                val otherUser = userRoomRepository.findOtherUser(room.room.id, userId)
                if (otherUser == null) {
                    room.room.name = "Error"
                } else {
                    room.room.name = otherUser.username
                }
            }
            RoomDTO(roomId = room.room.id.toString(), roomName = room.room.name, encrypted = room.room.encrypted, role = room.role, type = room.type)
        }

        return roomDTOs
    }

    // ==========================
    // Room membership
    // ==========================
    fun joinRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        roomRepository.findById(roomUUID).orElse(null) ?: throw RoomNotFoundException()

        if (bannedUserService.isUserBanned(userId, roomUUID)) throw BannedException()

        val userRoom = UserRoomEntity(UserRoomId(userId, roomUUID), RoomRole.MEMBER, RoomType.GROUP)
        userRoomRepository.save(userRoom)
    }

    @Transactional
    fun leaveRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val existed = userRoomRepository.existsByIdUserIdAndIdRoomId(userId, roomUUID)
        if (!existed) {
            throw RoomNotFoundException()
        }

        userRoomRepository.deleteByIdUserIdAndIdRoomId(userId, roomUUID)
    }

    // ==========================
    // Room updates and deletion
    // ==========================
    fun editRoom(roomDTO: RoomDTO) {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomId = try {
            UUID.fromString(roomDTO.roomId)
        } catch (_: IllegalArgumentException) {
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
    fun deleteRoom(roomId: String?){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomUUID)
            ?: throw RoomNotFoundException()

        if (userRoom.role != RoomRole.OWNER) {
            throw NotPermittedException()
        }

        val memberIds = userRoomRepository.findUsersByRoomId(roomUUID).map { it.id }

        val room = roomRepository.findById(roomUUID).orElseThrow { RoomNotFoundException() }
        roomRepository.delete(room)

        eventPublisher.publishEvent(RoomDeletedEvent(roomUUID, room.name, memberIds))
    }

    @Transactional
    fun removeUserFromRoom(administrationDTO: AdministrationDTO){
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomId: UUID;
        val targetId: UUID;
        val action: RoomAction;

        try {
            roomId = UUID.fromString(administrationDTO.roomId)
            targetId = UUID.fromString(administrationDTO.userId)
            action = administrationDTO.actions
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        if (targetId == userId) {
            throw InvalidBanException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (userRoom.role != RoomRole.OWNER) {
            throw NotPermittedException()
        }

        userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId)

        if (action == RoomAction.BAN) bannedUserService.banUser(targetId, roomId)

        eventPublisher.publishEvent(UserRemovedEvent(targetId, roomId, action, administrationDTO.reason))
    }

    fun unbanUser(unbanDTO: UnbanDTO) {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomId: UUID;
        val targetId: UUID;

        try {
            roomId = UUID.fromString(unbanDTO.roomId)
            targetId = UUID.fromString(unbanDTO.userId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        if (targetId == userId) {
            throw InvalidBanException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (userRoom.role != RoomRole.OWNER) {
            throw NotPermittedException()
        }

        bannedUserService.unbanUser(targetId, roomId)
    }

    fun getAllBansForRoom(roomIdString: String?): List<RoomUserDTO> {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val roomId = try {
            UUID.fromString(roomIdString)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (userRoom.role != RoomRole.OWNER) {
            throw NotPermittedException()
        }

        val bannedUserIds = bannedUserService.getBannedUserIds(roomId)
        val bannedUsers = userService.getAllById(bannedUserIds)

        return bannedUsers.map { user ->
            RoomUserDTO(
                id = user.id,
                username = user.username,
                avatarUrl = user.avatarUrl,
                online = false
            )
        }
    }

    // ==========================
    // Private message rooms
    // ==========================
    fun getOrStartPrivateMessage(username: String): UUID {
        val userId = getId()
        userService.getUserById(userId) ?: throw InvalidUserException()

        val friend = friendService.getFriendEntity(username, userId)

        val roomId = generatePrivateRoomId(friend.id, userId)
        val roomExists = roomRepository.findById(roomId).orElse(null)
        if (roomExists != null) {
            return roomExists.id
        }

        val room = roomRepository.save(
            RoomEntity(
                id = roomId,
                name = "",
                type = RoomType.PRIVATE,
                encrypted = false,
                keyVersion = null
            )
        )

        userRoomRepository.save(
            UserRoomEntity(
                id = UserRoomId(userId, room.id),
                role = RoomRole.MEMBER,
                type = RoomType.PRIVATE,
            )
        )

        userRoomRepository.save(
            UserRoomEntity(
                id = UserRoomId(friend.id, room.id),
                role = RoomRole.MEMBER,
                type = RoomType.PRIVATE,
            )
        )

        return room.id
    }

    // ==========================
    // Internal helper
    // ==========================
    private fun generatePrivateRoomId(user1: UUID, user2: UUID): UUID {
        if (user1 == user2) throw FriendYourselfException()

        val ordered = if (user1 < user2) {
            "${user1}:${user2}"
        } else {
            "${user2}:${user1}"
        }

        return UUID.nameUUIDFromBytes(ordered.toByteArray())
    }
}