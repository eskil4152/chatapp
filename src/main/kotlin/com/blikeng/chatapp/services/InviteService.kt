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
import com.blikeng.chatapp.errors.FriendYourselfException
import com.blikeng.chatapp.errors.InvalidInviteException
import com.blikeng.chatapp.errors.InviteNotFoundException
import com.blikeng.chatapp.errors.InvalidUUIDException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.repositories.InviteRepository
import com.blikeng.chatapp.repositories.UserRepository
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
    private val friendService: FriendService,
    private val inviteRepository: InviteRepository
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

        when (invite.type) {
            InviteType.FRIEND_REQUEST -> handleFriendRequestResponse(inviteResponseDTO.response, invite, id)
            InviteType.ROOM_INVITE -> handleRoomInviteResponse(inviteResponseDTO)
            InviteType.OPEN_ROOM_INVITE -> handleOpenRoomInviteResponse(inviteResponseDTO)
        }
    }

    fun sendRoomInvite(roomInviteDTO: RoomInviteDTO){}

    fun createOpenRoomInvite(openRoomInviteDTO: OpenRoomInviteDTO){}

    private fun handleFriendRequestResponse(response: InviteResponse, invite: InviteEntity, id: UUID){
        if (id != invite.toUserId) throw InviteNotFoundException()

        if (response == InviteResponse.REJECTED) {
            invite.status = InviteStatus.REJECTED
            return
        }

        friendService.addFriend(id, invite.fromUserId)
        invite.status = InviteStatus.ACCEPTED
    }

    private fun handleRoomInviteResponse(inviteResponseDTO: InviteResponseDTO){}
    private fun handleOpenRoomInviteResponse(inviteResponseDTO: InviteResponseDTO){}
}