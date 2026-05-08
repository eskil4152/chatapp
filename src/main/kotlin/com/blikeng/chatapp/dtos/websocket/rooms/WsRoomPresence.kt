package com.blikeng.chatapp.dtos.websocket.rooms

import java.util.UUID

data class WsRoomPresence(
    val type: String = "ROOM_PRESENCE",
    val roomId: UUID,
    val userId: UUID,
    val online: Boolean,
)
