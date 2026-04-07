package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.invites.FriendRequestDTO
import com.blikeng.chatapp.dtos.invites.InviteResponse
import com.blikeng.chatapp.dtos.invites.InviteResponseDTO
import com.blikeng.chatapp.dtos.invites.OpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.RoomInviteDTO
import com.blikeng.chatapp.entities.InviteEntity
import com.blikeng.chatapp.entities.InviteStatus
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.errors.AlreadyFriendsException
import com.blikeng.chatapp.errors.AlreadyInvitedException
import com.blikeng.chatapp.errors.BannedException
import com.blikeng.chatapp.errors.FriendYourselfException
import com.blikeng.chatapp.errors.InvalidInviteException
import com.blikeng.chatapp.errors.InviteNotFoundException
import com.blikeng.chatapp.errors.InvalidUUIDException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.InviteBannedUserException
import com.blikeng.chatapp.errors.InviteYourselfException
import com.blikeng.chatapp.errors.NotPermittedException
import com.blikeng.chatapp.errors.RoomNotFoundException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.repositories.InviteRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.auth.getId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class InviteService(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val userRoomRepository: UserRoomRepository,
    private val friendService: FriendService,
    private val inviteRepository: InviteRepository,
    private val roomService: RoomService,
    private val bannedUserService: BannedUserService
) {
    fun sendFriendRequest(friendRequestDTO: FriendRequestDTO) {
        if (friendRequestDTO.username.trim().isEmpty()) throw InvalidInviteException()

        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        val friend = userRepository.getUserByUsernameIgnoreCase(friendRequestDTO.username) ?: throw UserNotFoundException()

        if (id == friend.id) throw FriendYourselfException()

        if (inviteRepository.existsPendingFriendRequest(id, friend.id)) throw AlreadyInvitedException()
        if (friendService.areFriends(id, friend.id)) throw AlreadyFriendsException()

        val friendship = InviteEntity(
            type = InviteType.FRIEND_REQUEST,
            fromUserId = id,
            toUserId = friend.id,
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
            status = InviteStatus.PENDING
        )

        inviteRepository.save(friendship)
    }

    fun sendRoomInvite(roomInviteDTO: RoomInviteDTO){
        val targetId: UUID
        val roomId: UUID

        try {
            targetId = UUID.fromString(roomInviteDTO.targetUserId)
            roomId = UUID.fromString(roomInviteDTO.roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        if (id == targetId) throw InviteYourselfException()

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(id, roomId)
            ?: throw RoomNotFoundException()

        val room = roomService.getRoom(roomId)
        if (room.isEmpty) throw InvalidInviteException()

        if (!userRoom.role.isAtLeast(RoomPermissions.INVITE_USER)) throw NotPermittedException()

        userRepository.findById(targetId).orElseThrow { UserNotFoundException() }

        if (userRoomRepository.existsByIdUserIdAndIdRoomId(targetId, roomId)) throw AlreadyInvitedException()
        if (bannedUserService.isUserBanned(targetId, roomId)) throw InviteBannedUserException()

        if (inviteRepository.existsPendingRoomInvite(targetId, roomId)) throw AlreadyInvitedException()

        val invite = InviteEntity(
            type = InviteType.ROOM_INVITE,
            fromUserId = id,
            toUserId = targetId,
            roomId = roomId,
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
            status = InviteStatus.PENDING
        )

        inviteRepository.save(invite)
    }

    @Transactional
    fun respondToRequest(inviteResponseDTO: InviteResponseDTO) {
        val id = getId()
        if (userService.getUserById(id) == null) throw InvalidUserException()

        val inviteId: UUID
        try {
            inviteId = UUID.fromString(inviteResponseDTO.inviteId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val invite = inviteRepository.findById(inviteId).orElseThrow { InviteNotFoundException() }

        if (invite.status != InviteStatus.PENDING) throw InviteNotFoundException()
        if (invite.expiresAt.isBefore(Instant.now())) {
            invite.status = InviteStatus.EXPIRED
            inviteRepository.save(invite)
            throw InviteNotFoundException()
        }

        when (invite.type) {
            InviteType.FRIEND_REQUEST -> handleFriendRequestResponse(inviteResponseDTO.response, invite, id)
            InviteType.ROOM_INVITE -> handleRoomInviteResponse(inviteResponseDTO.response, invite, id)
            InviteType.OPEN_ROOM_INVITE -> handleOpenRoomInviteResponse(invite, id)
        }
    }

    fun createOpenRoomInvite(openRoomInviteDTO: OpenRoomInviteDTO){
        if (openRoomInviteDTO.maxUsages <= 0) throw InvalidInviteException()

        val roomId: UUID

        try {
            roomId = UUID.fromString(openRoomInviteDTO.roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        val room = roomService.getRoom(roomId)
        if (room.isEmpty) throw InvalidInviteException()

        val userRoom = userRoomRepository.findByIdUserIdAndIdRoomId(id, roomId)
            ?: throw RoomNotFoundException()

        if (!userRoom.role.isAtLeast(RoomPermissions.OPEN_INVITE)) throw NotPermittedException()

        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = id,
            roomId = roomId,
            expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
            maxUsages = openRoomInviteDTO.maxUsages,
            status = InviteStatus.PENDING
        )

        inviteRepository.save(invite)
    }

    private fun handleFriendRequestResponse(response: InviteResponse, invite: InviteEntity, id: UUID){
        if (id != invite.toUserId) throw InviteNotFoundException()

        if (response == InviteResponse.REJECTED) {
            invite.status = InviteStatus.REJECTED
            return
        }

        friendService.addFriend(id, invite.fromUserId)
        invite.status = InviteStatus.ACCEPTED
    }

    private fun handleRoomInviteResponse(response: InviteResponse, invite: InviteEntity, id: UUID){
        if (id != invite.toUserId) throw InviteNotFoundException()

        if (response == InviteResponse.REJECTED) {
            invite.status = InviteStatus.REJECTED
            return
        }

        if (invite.roomId == null) throw InvalidInviteException()
        roomService.joinRoom(id, invite.roomId!!)
    }

    private fun handleOpenRoomInviteResponse(invite: InviteEntity, id: UUID){
        if (invite.roomId == null) throw InvalidInviteException()

        if (userRoomRepository.existsByIdUserIdAndIdRoomId(id, invite.roomId!!)) throw AlreadyInvitedException()
        if (bannedUserService.isUserBanned(id, invite.roomId!!)) throw BannedException()

        val updated = inviteRepository.incrementUsagesIfAvailable(invite.id)
        if (updated == 0) throw InvalidInviteException()

        inviteRepository.deleteByToUserIdAndRoomId(id, invite.roomId!!)

        roomService.joinRoom(id, invite.roomId!!)

        val refreshed = inviteRepository.findById(invite.id).orElseThrow { InvalidInviteException() }
        if (refreshed.maxUsages != null && refreshed.usages != null && refreshed.usages!! >= refreshed.maxUsages!!) {
            refreshed.status = InviteStatus.EXHAUSTED
            inviteRepository.save(refreshed)
        }
    }
}