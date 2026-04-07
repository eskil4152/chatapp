package com.blikeng.chatapp.events

import java.util.UUID

data class UserJoinedRoomEvent(
    val userId: UUID,
    val username: String,
    val roomId: UUID,
)
