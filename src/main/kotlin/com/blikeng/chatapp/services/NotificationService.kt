package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.websocket.WsRoomAction
import com.blikeng.chatapp.dtos.websocket.WsRoomDeleted
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.events.UserRemovedEvent
import com.blikeng.chatapp.messaging.redis.PresenceKeys
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
) {
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