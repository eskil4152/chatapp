package com.blikeng.chatapp.dtos.websocket

import com.blikeng.chatapp.dtos.invites.PendingInviteDTO

data class WsPendingInviteSnapshot(
    val type: String = "PENDING_INVITES",
    val invites: List<PendingInviteDTO>
)
