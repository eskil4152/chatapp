package com.blikeng.chatapp.notifications.events

import java.util.UUID

data class UserLeftRoomEvent(
    val userId: UUID,
    val username: String,
    val roomId: UUID,
)
