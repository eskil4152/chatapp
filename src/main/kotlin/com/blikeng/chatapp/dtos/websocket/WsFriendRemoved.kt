package com.blikeng.chatapp.dtos.websocket

import java.util.UUID

data class WsFriendRemoved(
    val type: String = "FRIEND_REMOVED",
    val userId: UUID
)