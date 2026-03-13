package com.blikeng.chatapp.messaging.redis

import java.util.*

object PresenceKeys {
    fun userPresence(userId: UUID): String = "presence:user:$userId"
    fun roomPresence(roomId: UUID): String = "presence:room:$roomId"
}