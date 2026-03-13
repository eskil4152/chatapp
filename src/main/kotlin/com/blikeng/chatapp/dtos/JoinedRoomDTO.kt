package com.blikeng.chatapp.dtos

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType

data class JoinedRoomDTO(
    val room: RoomEntity,
    val role: RoomRole,
    val type: RoomType = RoomType.GROUP,
)