package com.blikeng.chatapp.dtos

import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType

data class RoomDTO (
    val roomName: String?,
    val roomId: String?,
    val encrypted: Boolean?,
    val role: RoomRole?,
    val type: RoomType?,
)