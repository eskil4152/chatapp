package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.room.AdministrationDTO
import com.blikeng.chatapp.dtos.room.ChangeRoleDTO
import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.dtos.room.RoomUserDTO
import com.blikeng.chatapp.dtos.room.UnbanDTO
import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.*
import com.blikeng.chatapp.errors.InvalidFieldException
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.events.UserJoinedRoomEvent
import com.blikeng.chatapp.events.UserRemovedEvent

import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.auth.getId
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.*

// ==========================
// Handles room creation, membership management, room updates, room deletion,
// and deterministic private message room creation between friends.
// joinRoom is an internal method called by InviteService on invite acceptance;
// direct join is not exposed as a public endpoint.
// ==========================
@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val userRoomRepository: UserRoomRepository,
    private val userService: UserService,
    private val friendService: FriendService,
    private val bannedUserService: BannedUserService,
    private val eventPublisher: ApplicationEventPublisher,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val roomListType = object : TypeReference<List<RoomDTO>>() {}
    private val ROOMS_CACHE_TTL = Duration.ofMinutes(5)

    private fun bustRoomsCache(vararg userIds: UUID) =
        userIds.forEach { redisTemplate.delete("user:$it:rooms") }
    // ==========================
    // Room creation and retrieval
    // ==========================
    fun makeNewRoom(roomName: String?, encrypted: Boolean?) {
        val userId = getId()

        val trimmedName = roomName?.trim()
        if (trimmedName.isNullOrEmpty()) throw InvalidRoomNameException()
        if (trimmedName.length > 100) throw InvalidRoomNameException()

        val room = roomRepository.save(
            RoomEntity(
                name = trimmedName,
                encrypted = encrypted == true,
                keyVersion = if (encrypted == true) 1 else null,
                type = RoomType.GROUP,
            ))

        val userRoom = UserRoomEntity(UserRoomId(userId, room.id), RoomRole.OWNER, RoomType.GROUP)
        userRoomRepository.save(userRoom)
        bustRoomsCache(userId)
    }

    fun getAllUserRooms(): List<RoomDTO> {
        val userId = getId()
        val key = "user:$userId:rooms"

        val cached = redisTemplate.opsForValue()[key]
        if (cached != null) return objectMapper.readValue(cached, roomListType)

        val joinedRooms = roomRepository.findRoomsForUser(userId)

        val privateRoomIds = joinedRooms.filter { it.type == RoomType.PRIVATE }.map { it.room.id }
        val dmPartners: Map<UUID, String> = if (privateRoomIds.isNotEmpty()) {
            userRoomRepository.findOtherUsersInPrivateRooms(privateRoomIds, userId)
                .associate { it.roomId to it.username }
        } else {
            emptyMap()
        }

        val rooms = joinedRooms.map { room ->
            val name = if (room.type == RoomType.PRIVATE) dmPartners[room.room.id] ?: "Error"
                       else room.room.name
            RoomDTO(roomId = room.room.id.toString(), roomName = name, encrypted = room.room.encrypted, role = room.role, type = room.type)
        }
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(rooms), ROOMS_CACHE_TTL)
        return rooms
    }

    fun getRoom(roomId: UUID): Optional<RoomEntity> {
        return roomRepository.findById(roomId)
    }

    fun getAllUsersInRoom(roomIdString: String): List<RoomUserDTO> {
        val roomId = try {
            UUID.fromString(roomIdString)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val userId = getId()

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (!userRoom.role.isAtLeast(RoomPermissions.VIEW_MEMBERS)) throw NotPermittedException()

        val userRooms = userRoomRepository.findUserRoomsByRoomId(roomId).associateBy { it.id.userId }
        val users = userService.getAllById(userRooms.keys.toList())

        return users.map {
            RoomUserDTO(
                id = it.id,
                username = it.username,
                avatarUrl = it.avatarUrl,
                online = false,
                role = userRooms[it.id]?.role,
            )
        }
    }

    // ==========================
    // Room membership
    // ==========================
    fun joinRoom(id: UUID, roomId: UUID) {
        val user = userService.getUserById(id) ?: throw InvalidUserException()
        val userRoom = UserRoomEntity(UserRoomId(id, roomId), RoomRole.MEMBER, RoomType.GROUP)
        userRoomRepository.save(userRoom)
        bustRoomsCache(id)
        eventPublisher.publishEvent(UserJoinedRoomEvent(userId = id, username = user.username, roomId = roomId))
    }

    @Transactional
    fun leaveRoom(roomId: String?){
        val userId = getId()

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
        bustRoomsCache(userId)
    }

    // ==========================
    // Room updates and deletion
    // ==========================
    fun editRoom(roomDTO: RoomDTO) {
        val userId = getId()

        val roomId = try {
            UUID.fromString(roomDTO.roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val name = roomDTO.roomName?.trim()
        if (name.isNullOrBlank()) throw InvalidRoomNameException()
        if (name.length > 100) throw InvalidRoomNameException()

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (!userRoom.role.isAtLeast(RoomPermissions.EDIT_ROOM)) throw NotPermittedException()

        val room = roomRepository.findById(roomId).orElse(null)
            ?: throw RoomNotFoundException()

        room.name = name
        roomRepository.save(room)

        val memberIds = userRoomRepository.findUsersByRoomId(roomId).map { it.id }
        bustRoomsCache(*memberIds.toTypedArray())
    }

    @Transactional
    fun deleteRoom(roomId: String?){
        val userId = getId()

        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomUUID)
            ?: throw RoomNotFoundException()

        if (!userRoom.role.isAtLeast(RoomPermissions.DELETE_ROOM)) throw NotPermittedException()

        val memberIds = userRoomRepository.findUsersByRoomId(roomUUID).map { it.id }

        val room = roomRepository.findById(roomUUID).orElseThrow { RoomNotFoundException() }
        roomRepository.delete(room)
        bustRoomsCache(*memberIds.toTypedArray())

        eventPublisher.publishEvent(RoomDeletedEvent(roomUUID, room.name, memberIds))
    }

    @Transactional
    fun changeRole(changeRoleDTO: ChangeRoleDTO) {
        val userId = getId()

        val (roomId, targetId) = parseIds(changeRoleDTO)

        if (targetId == userId) throw NotPermittedException()

        val userUserRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()
        val targetUserRoom = userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId)
            ?: throw RoomNotFoundException()

        if (targetUserRoom.role == RoomRole.OWNER) throw NotPermittedException()

        val newRole = resolveNewRole(changeRoleDTO.action, userUserRoom.role, targetUserRoom.role)

        targetUserRoom.role = newRole
        userRoomRepository.save(targetUserRoom)
        bustRoomsCache(targetId)
    }

    @Transactional
    fun removeUserFromRoom(administrationDTO: AdministrationDTO){
        val userId = getId()

        val roomId: UUID;
        val targetId: UUID;
        val action: RoomAction;

        try {
            roomId = UUID.fromString(administrationDTO.roomId)
            targetId = UUID.fromString(administrationDTO.userId)
            action = administrationDTO.action
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val reason = administrationDTO.reason.trim()
        if (reason.length > 500) throw InvalidFieldException()

        if (targetId == userId) {
            throw InvalidBanException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        val requiredPermission = if (action == RoomAction.BAN) RoomPermissions.BAN_USER else RoomPermissions.KICK_USER
        if (!userRoom.role.isAtLeast(requiredPermission)) throw NotPermittedException()

        userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId)
        bustRoomsCache(targetId)

        if (action == RoomAction.BAN) bannedUserService.banUser(targetId, roomId)

        eventPublisher.publishEvent(UserRemovedEvent(targetId, roomId, action, reason))
    }

    fun unbanUser(unbanDTO: UnbanDTO) {
        val userId = getId()

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

        if (!userRoom.role.isAtLeast(RoomPermissions.UNBAN_USER)) throw NotPermittedException()

        bannedUserService.unbanUser(targetId, roomId)
    }

    fun getAllBansForRoom(roomIdString: String?): List<RoomUserDTO> {
        val userId = getId()

        val roomId = try {
            UUID.fromString(roomIdString)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId)
            ?: throw RoomNotFoundException()

        if (!userRoom.role.isAtLeast(RoomPermissions.VIEW_BANS)) throw NotPermittedException()

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
    fun getOrStartPrivateMessage(userIdDTO: UserIdDTO): UUID {
        val userId = getId()

        val friendId = try {
            UUID.fromString(userIdDTO.userId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val friend = friendService.getFriendEntityById(friendId, userId)

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
        bustRoomsCache(userId, friend.id)

        return room.id
    }

    // ==========================
    // Internal helpers
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

    private fun parseIds(dto: ChangeRoleDTO): Pair<UUID, UUID> {
        return try {
            UUID.fromString(dto.roomId) to UUID.fromString(dto.userId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }
    }

    private fun resolveNewRole(
        action: RoleAction,
        actorRole: RoomRole,
        targetRole: RoomRole
    ): RoomRole {
        return when (action) {
            RoleAction.PROMOTE -> promote(actorRole, targetRole)
            RoleAction.DEMOTE -> demote(actorRole, targetRole)
        }
    }

    private fun promote(actor: RoomRole, target: RoomRole): RoomRole {
        return when (target) {
            RoomRole.MEMBER -> {
                requirePermission(actor, RoomPermissions.PROMOTE_TO_MODERATOR)
                RoomRole.MODERATOR
            }
            RoomRole.MODERATOR -> {
                requirePermission(actor, RoomPermissions.PROMOTE_TO_ADMIN)
                RoomRole.ADMIN
            }
            else -> throw NotPermittedException()
        }
    }

    private fun demote(actor: RoomRole, target: RoomRole): RoomRole {
        return when (target) {
            RoomRole.ADMIN -> {
                requirePermission(actor, RoomPermissions.DEMOTE_TO_MODERATOR)
                RoomRole.MODERATOR
            }
            RoomRole.MODERATOR -> {
                requirePermission(actor, RoomPermissions.DEMOTE_TO_MEMBER)
                RoomRole.MEMBER
            }
            else -> throw NotPermittedException()
        }
    }

    private fun requirePermission(actorRole: RoomRole, requiredRole: RoomRole) {
        if (!actorRole.isAtLeast(requiredRole)) throw NotPermittedException()
    }
}