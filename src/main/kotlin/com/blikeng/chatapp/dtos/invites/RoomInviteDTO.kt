package com.blikeng.chatapp.dtos.invites

data class RoomInviteDTO (
    val type: String,
    val targetUserId: String,
    val roomId: String,
    val expiresAt: Long
)