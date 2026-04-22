package com.blikeng.chatapp.events

import java.util.UUID

data class UserLeftRoomEvent(
    val userId: UUID,
    val username: String,
    val roomId: UUID,
)