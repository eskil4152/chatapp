package com.blikeng.chatapp.dtos.websocket.rooms

import java.util.UUID

data class WsRoomDeleted(
    val type: String = "ROOM_DELETED",
    val roomId: UUID,
    val roomName: String,
)
