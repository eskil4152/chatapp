package com.blikeng.chatapp.dtos.invites

data class RoomInviteDTO(
    val type: String,
    val targetUsername: String,
    val roomId: String,
)
