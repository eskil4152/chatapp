package com.blikeng.chatapp.messaging.redis

import java.util.*

// ==========================
// Centralized Redis key builders for user presence tracking.
// ==========================
object PresenceKeys {
    fun userPresence(userId: UUID): String = "presence:user:$userId"
    fun userChannel(userId: UUID): String = "user:$userId"
}
