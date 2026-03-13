package com.blikeng.chatapp.messaging.redis

import java.util.UUID

object PresenceKeys {
    fun userPresence(userId: UUID): String = "presence:user:$userId"
    fun roomPresence(roomId: UUID): String = "presence:room:$roomId"
}