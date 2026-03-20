package com.blikeng.chatapp.dtos.websocket

import com.blikeng.chatapp.dtos.room.RoomUserDTO
import java.util.*

data class WsRoomUsers (
    val type: String = "USERS_IN_ROOM",
    val roomId: UUID,
    val users: List<RoomUserDTO>,
)