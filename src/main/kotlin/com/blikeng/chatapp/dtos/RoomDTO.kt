package com.blikeng.chatapp.dtos

import com.blikeng.chatapp.entities.RoomRole

data class RoomDTO (
    val roomName: String?,
    val roomId: String?,
    val encrypted: Boolean?,
    val role: RoomRole?,
)