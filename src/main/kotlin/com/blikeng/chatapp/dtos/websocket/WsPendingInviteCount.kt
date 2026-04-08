package com.blikeng.chatapp.dtos.websocket

data class WsPendingInviteCount(
    val type: String = "PENDING_INVITES",
    val count: Int,
)
