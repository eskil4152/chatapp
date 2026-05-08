package com.blikeng.chatapp.dtos.websocket.friends

import java.util.UUID

data class WsFriendAdded(
    val type: String = "FRIEND_ADDED",
    val userId: UUID,
    val username: String?,
    val online: Boolean,
    val avatarUrl: String?,
)
