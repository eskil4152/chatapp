package com.blikeng.chatapp.dtos.websocket.rooms

import java.util.UUID

data class WsRoomAction(
    val type: String = "ROOM_ACTION",
    val roomId: UUID,
    val action: String,
    val reason: String?,
)
