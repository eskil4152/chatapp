package com.blikeng.chatapp.dtos.websocket

import java.util.*

data class WsRoomPresence (
    val type: String = "ROOM_PRESENCE",
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val avatarUrl: String?,
    val online: Boolean,
)
