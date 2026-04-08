package com.blikeng.chatapp.dtos.invites

data class OpenRoomInviteDTO (
    val type: String,
    val roomId: String,
    val maxUsages: Int,
    val expiresAt: Long? = null,
)