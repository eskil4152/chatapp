package com.blikeng.chatapp.dtos.websocket

import com.blikeng.chatapp.entities.RoomRole
import java.util.UUID

data class WsRoomJoined (
    val type: String = "ROOM_JOINED",
    val roomId: UUID,
    val members: List<RoomMember>,
    val roomName: String,
    val encrypted: Boolean,
    val role: RoomRole
)

data class RoomMember(
    val id: UUID,
    val username: String,
    val avatar: String?,
    val online: Boolean,
)
