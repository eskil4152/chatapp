package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.websocket.WsChat
import com.blikeng.chatapp.dtos.websocket.WsFriendPresence
import com.blikeng.chatapp.dtos.websocket.WsInviteAccepted
import com.blikeng.chatapp.dtos.websocket.WsInviteReceived
import com.blikeng.chatapp.dtos.websocket.WsRoomAction
import com.blikeng.chatapp.dtos.websocket.WsRoomDeleted
import com.blikeng.chatapp.dtos.websocket.WsRoomPresence
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.events.InviteAcceptedEvent
import com.blikeng.chatapp.events.InviteSentEvent
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.events.UserJoinedRoomEvent
import com.blikeng.chatapp.events.UserRemovedEvent
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import java.time.Instant
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.*

@Service
class NotificationService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
    private val presenceHandler: PresenceHandler,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInviteSent(event: InviteSentEvent) {
        val payload = objectMapper.writeValueAsString(
            WsInviteReceived(
                id = event.invite.id,
                inviteType = event.invite.type,
                fromUserId = event.invite.fromUserId,
                roomId = event.invite.roomId,
                expiresAt = event.invite.expiresAt,
            )
        )

        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.toUserId), payload)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onInviteAccepted(event: InviteAcceptedEvent) {
        val payload = objectMapper.writeValueAsString(
            WsInviteAccepted(inviteType = event.type, roomId = event.roomId, username = event.toUsername, avatarUrl = event.toAvatarUrl)
        )
        redisTemplate.convertAndSend(PresenceKeys.userChannel(event.fromUserId), payload)

        if (event.type == InviteType.FRIEND_REQUEST) {
            redisTemplate.convertAndSend(
                PresenceKeys.userChannel(event.fromUserId),
                objectMapper.writeValueAsString(WsFriendPresence(userId = event.toUserId, online = presenceHandler.isUserOnline(event.toUserId)))
            )
            redisTemplate.convertAndSend(
                PresenceKeys.userChannel(event.toUserId),
                objectMapper.writeValueAsString(WsFriendPresence(userId = event.fromUserId, online = presenceHandler.isUserOnline(event.fromUserId)))
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserJoinedRoom(event: UserJoinedRoomEvent) {
        val payload = objectMapper.writeValueAsString(
            WsChat(type = "JOIN", username = event.username, content = "", timestamp = Instant.now())
        )
        redisTemplate.convertAndSend(PresenceKeys.roomChannel(event.roomId), payload)

        val presencePayload = objectMapper.writeValueAsString(
            WsRoomPresence(roomId = event.roomId, userId = event.userId, online = presenceHandler.isUserOnline(event.userId))
        )
        redisTemplate.convertAndSend(PresenceKeys.roomChannel(event.roomId), presencePayload)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUserRemoved(event: UserRemovedEvent) {
        val action = when (event.action) {
            RoomAction.KICK -> "KICKED"
            RoomAction.BAN -> "BANNED"
        }

        val payload = objectMapper.writeValueAsString(
            WsRoomAction(roomId = event.roomId, action = action, reason = event.reason)
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
}