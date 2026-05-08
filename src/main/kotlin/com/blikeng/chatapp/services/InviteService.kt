package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.invites.FriendRequestDTO
import com.blikeng.chatapp.dtos.invites.InviteResponse
import com.blikeng.chatapp.dtos.invites.InviteResponseDTO
import com.blikeng.chatapp.dtos.invites.OpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.PendingInviteDTO
import com.blikeng.chatapp.dtos.invites.RoomInviteDTO
import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingFriendRequestDTO
import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingInvitationDTO
import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingOpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingRoomInviteDTO
import com.blikeng.chatapp.entities.InviteEntity
import com.blikeng.chatapp.entities.InviteStatus
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.AlreadyFriendsException
import com.blikeng.chatapp.errors.AlreadyInvitedException
import com.blikeng.chatapp.errors.AlreadyMemberException
import com.blikeng.chatapp.errors.BannedException
import com.blikeng.chatapp.errors.FriendYourselfException
import com.blikeng.chatapp.errors.InvalidInviteException
import com.blikeng.chatapp.errors.InvalidUUIDException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.InviteBannedUserException
import com.blikeng.chatapp.errors.InviteNotFoundException
import com.blikeng.chatapp.errors.InviteYourselfException
import com.blikeng.chatapp.errors.NotPermittedException
import com.blikeng.chatapp.errors.RoomNotFoundException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.notifications.events.InviteAcceptedEvent
import com.blikeng.chatapp.notifications.events.InviteSentEvent
import com.blikeng.chatapp.repositories.InviteRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.auth.getId
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// ==========================
// Manages all invite lifecycle operations: sending friend requests and room invites,
// creating open room invites with usage limits (returns the invite UUID as a shareable code),
// listing pending invites for the current user, and responding to pending invites (accept/reject).
// Accepting a friend request calls FriendService.addFriend; accepting a room invite calls RoomService.joinRoom.
// ==========================
@Service
class InviteService(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val userRoomRepository: UserRoomRepository,
    private val friendService: FriendService,
    private val inviteRepository: InviteRepository,
    private val roomService: RoomService,
    private val bannedUserService: BannedUserService,
    private val eventPublisher: ApplicationEventPublisher,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val pendingInviteListType = object : TypeReference<List<PendingInviteDTO>>() {}
    private val pendingInvitesTTL = Duration.ofMinutes(5)

    fun getPendingInvites(): List<PendingInviteDTO> = getPendingInvites(getId())

    fun getPendingInvites(userId: UUID): List<PendingInviteDTO> {
        val key = "user:$userId:pending_invites"
        val cached = redisTemplate.opsForValue()[key]
        if (cached != null) return objectMapper.readValue(cached, pendingInviteListType)

        val invites =
            inviteRepository.findByToUserIdAndStatus(userId, InviteStatus.PENDING).map {
                val sender = userService.getUserById(it.fromUserId)
                val roomName = it.roomId?.let { roomId -> roomService.getRoom(roomId).orElse(null)?.name }

                PendingInviteDTO(
                    id = it.id,
                    type = it.type,
                    fromUserId = it.fromUserId,
                    fromUsername = sender?.username ?: "Unknown",
                    fromAvatarUrl = sender?.avatarUrl,
                    roomId = it.roomId,
                    roomName = roomName,
                    expiresAt = it.expiresAt,
                )
            }
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(invites), pendingInvitesTTL)
        return invites
    }

    fun getOutgoingInvites(): List<OutgoingInvitationDTO> {
        val id = getId()
        return inviteRepository.findByFromUserIdAndStatus(id, InviteStatus.PENDING).map {
            when (it.type) {
                InviteType.FRIEND_REQUEST -> {
                    val friendId = it.toUserId ?: throw InvalidInviteException()
                    val friend = userRepository.findById(friendId).orElseThrow { UserNotFoundException() }

                    OutgoingFriendRequestDTO(
                        id = it.id,
                        type = it.type,
                        fromUserId = it.fromUserId,
                        toUserId = friend.id,
                        toUsername = friend.username,
                        avatar = friend.avatarUrl,
                        expiresAt = it.expiresAt,
                    )
                }

                InviteType.ROOM_INVITE -> {
                    val toUserId = it.toUserId ?: throw InvalidInviteException()
                    val user = userRepository.findById(toUserId).orElseThrow { UserNotFoundException() }

                    val roomId = it.roomId ?: throw InvalidInviteException()

                    OutgoingRoomInviteDTO(
                        id = it.id,
                        type = it.type,
                        fromUserId = it.fromUserId,
                        toUserId = user.id,
                        toUsername = user.username,
                        avatar = user.avatarUrl,
                        roomId = roomId,
                        expiresAt = it.expiresAt,
                    )
                }

                InviteType.OPEN_ROOM_INVITE -> {
                    val roomId = it.roomId ?: throw InvalidInviteException()
                    val usages = it.usages ?: throw InvalidInviteException()
                    val maxUsages = it.maxUsages ?: throw InvalidInviteException()

                    OutgoingOpenRoomInviteDTO(
                        id = it.id,
                        type = it.type,
                        fromUserId = it.fromUserId,
                        roomId = roomId,
                        usages = usages,
                        maxUsages = maxUsages,
                        expiresAt = it.expiresAt,
                    )
                }
            }
        }
    }

    @Transactional
    fun sendFriendRequest(friendRequestDTO: FriendRequestDTO) {
        if (friendRequestDTO.username.trim().isEmpty()) throw InvalidInviteException()

        val id = getId()
        val sender = userService.getUserById(id) ?: throw InvalidUserException()

        val friend = userRepository.getUserByUsernameIgnoreCase(friendRequestDTO.username) ?: throw UserNotFoundException()

        if (id == friend.id) throw FriendYourselfException()

        if (inviteRepository.existsPendingFriendRequest(id, friend.id)) throw AlreadyInvitedException()
        if (friendService.areFriends(id, friend.id)) throw AlreadyFriendsException()

        val friendship =
            InviteEntity(
                type = InviteType.FRIEND_REQUEST,
                fromUserId = id,
                toUserId = friend.id,
                expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                status = InviteStatus.PENDING,
            )

        val saved = inviteRepository.save(friendship)
        redisTemplate.delete("user:${friend.id}:pending_invites")
        eventPublisher.publishEvent(
            InviteSentEvent(
                toUserId = friend.id,
                invite =
                    PendingInviteDTO(
                        id = saved.id,
                        type = saved.type,
                        fromUserId = saved.fromUserId,
                        fromUsername = sender.username,
                        fromAvatarUrl = sender.avatarUrl,
                        roomId = saved.roomId,
                        roomName = saved.roomId?.let { roomId -> roomService.getRoom(roomId).orElse(null)?.name },
                        expiresAt = saved.expiresAt,
                    ),
            ),
        )
    }

    @Transactional
    fun sendRoomInvite(roomInviteDTO: RoomInviteDTO) {
        val targetUsername = roomInviteDTO.targetUsername.trim()
        val roomId: UUID

        try {
            roomId = UUID.fromString(roomInviteDTO.roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val id = getId()
        val sender = userService.getUserById(id) ?: throw InvalidUserException()

        val userRoom =
            userRoomRepository.findByIdUserIdAndIdRoomId(id, roomId)
                ?: throw RoomNotFoundException()

        val room = roomService.getRoom(roomId)
        if (room.isEmpty) throw InvalidInviteException()

        if (!userRoom.role.isAtLeast(RoomPermissions.INVITE_USER)) throw NotPermittedException()

        val target = userRepository.getUserByUsernameIgnoreCase(targetUsername) ?: throw UserNotFoundException()

        if (id == target.id) throw InviteYourselfException()

        if (userRoomRepository.existsByIdUserIdAndIdRoomId(target.id, roomId)) throw AlreadyMemberException()
        if (bannedUserService.isUserBanned(target.id, roomId)) throw InviteBannedUserException()
        if (inviteRepository.existsPendingRoomInvite(target.id, roomId)) throw AlreadyInvitedException()

        val invite =
            InviteEntity(
                type = InviteType.ROOM_INVITE,
                fromUserId = id,
                toUserId = target.id,
                roomId = roomId,
                expiresAt = Instant.now().plus(7, ChronoUnit.DAYS),
                status = InviteStatus.PENDING,
            )

        val saved = inviteRepository.save(invite)
        redisTemplate.delete("user:${target.id}:pending_invites")
        eventPublisher.publishEvent(
            InviteSentEvent(
                toUserId = target.id,
                invite =
                    PendingInviteDTO(
                        id = saved.id,
                        type = saved.type,
                        fromUserId = saved.fromUserId,
                        fromUsername = sender.username,
                        fromAvatarUrl = sender.avatarUrl,
                        roomId = saved.roomId,
                        roomName = saved.roomId?.let { roomId -> roomService.getRoom(roomId).orElse(null)?.name },
                        expiresAt = saved.expiresAt,
                    ),
            ),
        )
    }

    @Transactional
    fun respondToRequest(inviteResponseDTO: InviteResponseDTO) {
        val id = getId()
        val acceptor = userService.getUserById(id) ?: throw InvalidUserException()

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

        redisTemplate.delete("user:$id:pending_invites")
        when (invite.type) {
            InviteType.FRIEND_REQUEST -> handleFriendRequestResponse(inviteResponseDTO.response, invite, acceptor)
            InviteType.ROOM_INVITE -> handleRoomInviteResponse(inviteResponseDTO.response, invite, acceptor)
            InviteType.OPEN_ROOM_INVITE -> handleOpenRoomInviteResponse(invite, id)
        }
    }

    fun createOpenRoomInvite(openRoomInviteDTO: OpenRoomInviteDTO): UUID {
        if (openRoomInviteDTO.maxUsages <= 0) throw InvalidInviteException()

        val roomId: UUID

        try {
            roomId = UUID.fromString(openRoomInviteDTO.roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val id = getId()
        val room = roomService.getRoom(roomId)
        if (room.isEmpty) throw InvalidInviteException()

        val userRoom =
            userRoomRepository.findByIdUserIdAndIdRoomId(id, roomId)
                ?: throw RoomNotFoundException()

        if (!userRoom.role.isAtLeast(RoomPermissions.OPEN_INVITE)) throw NotPermittedException()

        val invite =
            InviteEntity(
                type = InviteType.OPEN_ROOM_INVITE,
                fromUserId = id,
                roomId = roomId,
                usages = 0,
                expiresAt =
                    openRoomInviteDTO.expiresAt
                        ?.let { Instant.ofEpochMilli(it) }
                        ?: Instant.now().plus(7, ChronoUnit.DAYS),
                maxUsages = openRoomInviteDTO.maxUsages,
                status = InviteStatus.PENDING,
            )

        return inviteRepository.save(invite).id
    }

    private fun handleFriendRequestResponse(
        response: InviteResponse,
        invite: InviteEntity,
        acceptor: UserEntity,
    ) {
        if (acceptor.id != invite.toUserId) throw InviteNotFoundException()

        if (response == InviteResponse.REJECTED) {
            invite.status = InviteStatus.REJECTED
            return
        }

        val sender = userService.getUserById(invite.fromUserId) ?: throw InvalidUserException()

        friendService.addFriend(acceptor.id, invite.fromUserId)
        invite.status = InviteStatus.ACCEPTED

        eventPublisher.publishEvent(
            InviteAcceptedEvent(
                fromUserId = invite.fromUserId,
                fromUsername = sender.username,
                fromAvatarUrl = sender.avatarUrl,
                toUserId = acceptor.id,
                toUsername = acceptor.username,
                toAvatarUrl = acceptor.avatarUrl,
                type = invite.type,
                roomId = invite.roomId,
            ),
        )
    }

    private fun handleRoomInviteResponse(
        response: InviteResponse,
        invite: InviteEntity,
        acceptor: UserEntity,
    ) {
        if (acceptor.id != invite.toUserId) throw InviteNotFoundException()

        if (response == InviteResponse.REJECTED) {
            invite.status = InviteStatus.REJECTED
            return
        }

        val sender = userService.getUserById(invite.fromUserId) ?: throw InvalidUserException()

        val roomId = invite.roomId ?: throw InvalidInviteException()

        roomService.joinRoom(acceptor.id, roomId)
        invite.status = InviteStatus.ACCEPTED

        eventPublisher.publishEvent(
            InviteAcceptedEvent(
                fromUserId = invite.fromUserId,
                fromUsername = sender.username,
                fromAvatarUrl = sender.avatarUrl,
                toUserId = acceptor.id,
                toUsername = acceptor.username,
                toAvatarUrl = acceptor.avatarUrl,
                type = invite.type,
                roomId = invite.roomId,
            ),
        )
    }

    private fun handleOpenRoomInviteResponse(
        invite: InviteEntity,
        id: UUID,
    ) {
        val roomId = invite.roomId ?: throw InvalidInviteException()

        if (userRoomRepository.existsByIdUserIdAndIdRoomId(id, roomId)) throw AlreadyInvitedException()
        if (bannedUserService.isUserBanned(id, roomId)) throw BannedException()

        val updated = inviteRepository.incrementUsagesIfAvailable(invite.id)
        if (updated == 0) throw InvalidInviteException()

        inviteRepository.deleteByToUserIdAndRoomId(id, roomId)

        roomService.joinRoom(id, roomId)

        val refreshed = inviteRepository.findById(invite.id).orElseThrow { InvalidInviteException() }
        val usages = refreshed.usages
        val maxUsages = refreshed.maxUsages

        if (usages != null && maxUsages != null && usages >= maxUsages) {
            refreshed.status = InviteStatus.EXHAUSTED
            inviteRepository.save(refreshed)
        }
    }
}
