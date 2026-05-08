package com.blikeng.chatapp.dtos.websocket.friends

import java.util.UUID

data class WsFriendSnapshot(
    val type: String = "FRIEND_SNAPSHOT",
    val friends: List<OnlineFriend>,
)

data class OnlineFriend(
    val userId: UUID,
    val username: String,
    val avatarUrl: String?,
    val online: Boolean,
)
