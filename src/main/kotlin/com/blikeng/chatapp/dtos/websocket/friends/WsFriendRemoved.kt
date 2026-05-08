package com.blikeng.chatapp.dtos.websocket.friends

import java.util.UUID

data class WsFriendRemoved(
    val type: String = "FRIEND_REMOVED",
    val userId: UUID,
)
