package com.blikeng.chatapp.dtos.websocket;

import java.util.UUID;

data class WsFriendPresence(
        val type: String = "FRIEND_PRESENCE",
        val userId: UUID,
        val online: Boolean
)
