package com.blikeng.chatapp.notifications

import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.websocket.WsBannedEvent
import com.blikeng.chatapp.dtos.websocket.WsUserRoleChanged
import com.blikeng.chatapp.dtos.websocket.chat.WsChat
import com.blikeng.chatapp.dtos.websocket.friends.WsFriendAdded
import com.blikeng.chatapp.dtos.websocket.friends.WsFriendRemoved
import com.blikeng.chatapp.dtos.websocket.invites.WsInviteAccepted
import com.blikeng.chatapp.dtos.websocket.invites.WsInviteReceived
import com.blikeng.chatapp.dtos.websocket.rooms.WsRoomAction
import com.blikeng.chatapp.dtos.websocket.rooms.WsRoomDeleted
import com.blikeng.chatapp.dtos.websocket.rooms.WsRoomPresence
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import com.blikeng.chatapp.notifications.events.FriendRemovedEvent
import com.blikeng.chatapp.notifications.events.InviteAcceptedEvent
import com.blikeng.chatapp.notifications.events.InviteSentEvent
import com.blikeng.chatapp.notifications.events.RoomDeletedEvent
import com.blikeng.chatapp.notifications.events.RoomPresenceEvent
import com.blikeng.chatapp.notifications.events.UserBannedEvent
import com.blikeng.chatapp.notifications.events.UserJoinedRoomEvent
import com.blikeng.chatapp.notifications.events.UserLeftRoomEvent
import com.blikeng.chatapp.notifications.events.UserRemovedEvent
import com.blikeng.chatapp.notifications.events.UserRoleChangedEvent
import com.blikeng.chatapp.services.UserRevocationService
import com.blikeng.chatapp.websocket.SessionRegistry
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.util.UUID

@Service
class NotificationDispatcher(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val presenceHandler: PresenceHandler,
    private val userRevocationService: UserRevocationService,
    private val sessionRegistry: SessionRegistry,
    private val rabbitTemplate: RabbitTemplate,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInviteSent(event: InviteSentEvent) {
        val payload =
            objectMapper.writeValueAsString(
                WsInviteReceived(
                    id = event.invite.id,
                    inviteType = event.invite.type,
                    fromUsername = event.invite.fromUsername,
                    roomName = event.invite.roomName,
                    fromAvatarUrl = event.invite.fromAvatarUrl,
                ),
            )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.toUserId), payload)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInviteAccepted(event: InviteAcceptedEvent) {
        val payload =
            objectMapper.writeValueAsString(
                WsInviteAccepted(
                    inviteType = event.type,
                    roomId = event.roomId,
                    username = event.toUsername,
                    avatarUrl = event.toAvatarUrl,
                ),
            )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.fromUserId), payload)

        if (event.type == InviteType.FRIEND_REQUEST) {
            redisTemplate.convertAndSend(
                PresenceKeys.userChannel(event.fromUserId),
                objectMapper.writeValueAsString(
                    WsFriendAdded(
                        userId = event.toUserId,
                        username = event.toUsername,
                        avatarUrl = event.toAvatarUrl,
                        online = presenceHandler.isUserOnline(event.toUserId),
                    ),
                ),
            )

            redisTemplate.convertAndSend(
                PresenceKeys.userChannel(event.toUserId),
                objectMapper.writeValueAsString(
                    WsFriendAdded(
                        userId = event.fromUserId,
                        username = event.fromUsername,
                        avatarUrl = event.fromAvatarUrl,
                        online = presenceHandler.isUserOnline(event.fromUserId),
                    ),
                ),
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserJoinedRoom(event: UserJoinedRoomEvent) =
        broadcastMembershipChange(event.roomId, event.userId, event.username, "JOIN", "${event.username} joined the room!")

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserLeftRoom(event: UserLeftRoomEvent) =
        broadcastMembershipChange(event.roomId, event.userId, event.username, "LEAVE", "${event.username} left the room.")

    private fun broadcastMembershipChange(
        roomId: UUID,
        userId: UUID,
        username: String,
        type: String,
        message: String,
    ) {
        val chatPayload =
            objectMapper.writeValueAsString(
                WsChat(type = type, username = "Server", content = message, userId = userId, timestamp = Instant.now()),
            )
        redisTemplate.convertAndSend(PresenceKeys.roomChannel(roomId), chatPayload)

        val presencePayload =
            objectMapper.writeValueAsString(
                WsRoomPresence(roomId = roomId, userId = userId, online = presenceHandler.isUserOnline(userId)),
            )
        redisTemplate.convertAndSend(PresenceKeys.roomChannel(roomId), presencePayload)

        val rabbitMessage =
            RabbitMessageDTO(
                id = UUID.randomUUID(),
                username = "Server",
                roomId = roomId,
                userId = userId,
                message = message,
            )
        redisTemplate.opsForList().rightPush("chat.peek.$roomId", objectMapper.writeValueAsString(rabbitMessage))
        rabbitTemplate.convertAndSend("chat.buffer", rabbitMessage)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserRemoved(event: UserRemovedEvent) {
        val action =
            when (event.action) {
                RoomAction.KICK -> "KICKED"
                RoomAction.BAN -> "BANNED"
            }

        val payload =
            objectMapper.writeValueAsString(
                WsRoomAction(roomId = event.roomId, action = action, reason = event.reason),
            )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.targetId), payload)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onRoomDeleted(event: RoomDeletedEvent) {
        val payload = objectMapper.writeValueAsString(WsRoomDeleted(roomId = event.roomId, roomName = event.roomName))

        event.memberIds.forEach { memberId ->
            redisTemplate.convertAndSend(PresenceKeys.userChannel(memberId), payload)
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserRoleChanges(event: UserRoleChangedEvent) {
        val payload =
            objectMapper.writeValueAsString(
                WsUserRoleChanged(
                    userId = event.userId,
                    byUsername = event.byUsername,
                    newRole = event.newRole,
                    action = event.action,
                ),
            )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.userId), payload)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserBanned(event: UserBannedEvent) {
        val payload =
            objectMapper.writeValueAsString(
                WsBannedEvent(
                    byUsername = event.byUsername,
                    reason = event.reason,
                ),
            )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.userId), payload)
        userRevocationService.revokeBanned(event.userId)
        sessionRegistry.closeUserSessions(event.userId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onFriendRemoved(event: FriendRemovedEvent) {
        val payloadFrom =
            objectMapper.writeValueAsString(
                WsFriendRemoved(userId = event.userId),
            )

        val payloadTo =
            objectMapper.writeValueAsString(
                WsFriendRemoved(userId = event.friendId),
            )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.userId), payloadFrom)
        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.friendId), payloadTo)
    }

    fun onRoomJoined(event: RoomPresenceEvent) {
        val roomId = event.roomId

        val payload =
            objectMapper.writeValueAsString(
                WsRoomPresence(roomId = roomId, userId = event.userId, online = event.online),
            )

        redisTemplate.convertAndSend(PresenceKeys.roomChannel(roomId), payload)
    }
}
