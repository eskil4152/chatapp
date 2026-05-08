package com.blikeng.chatapp.messaging.redis

import java.util.UUID

// ==========================
// Centralized Redis key builders for user presence tracking.
// ==========================
object PresenceKeys {
    fun userPresence(userId: UUID): String = "presence:user:$userId"

    fun userChannel(userId: UUID): String = "user:$userId"

    fun roomChannel(roomId: UUID): String = "room:$roomId"

    fun roomMembersKey(roomId: UUID): String = "room:$roomId:members"
}
