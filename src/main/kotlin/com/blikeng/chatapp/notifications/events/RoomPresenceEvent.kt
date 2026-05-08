package com.blikeng.chatapp.notifications.events

import java.util.UUID

data class RoomPresenceEvent(
    val roomId: UUID,
    val userId: UUID,
    val online: Boolean,
)
