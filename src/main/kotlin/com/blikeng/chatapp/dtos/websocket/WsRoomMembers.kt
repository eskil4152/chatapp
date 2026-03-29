package com.blikeng.chatapp.dtos.websocket

import java.util.UUID

data class WsRoomMembers (
    val type: String = "ROOM_MEMBERS",
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val avatarUrl: String?,
    val online: Boolean,
)
