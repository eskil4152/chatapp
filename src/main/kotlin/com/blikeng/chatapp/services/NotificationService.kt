package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.websocket.WsRoomAction
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.*

@Service
class NotificationService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    fun notifyRoomAction(userId: UUID, roomId: UUID, action: String, reason: String?) {
        val payload = objectMapper.writeValueAsString(
            WsRoomAction(roomId = roomId, action = action, reason = reason)
        )
        redisTemplate.convertAndSend(PresenceKeys.userChannel(userId), payload)
    }
}