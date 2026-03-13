package com.blikeng.chatapp.messaging.redis

import java.util.*

// ==========================
// Centralized Redis key builders for user and room presence tracking.
// ==========================
object PresenceKeys {
    fun userPresence(userId: UUID): String = "presence:user:$userId"
    fun roomPresence(roomId: UUID): String = "presence:room:$roomId"
}