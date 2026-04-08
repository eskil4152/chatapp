package com.blikeng.chatapp.dtos.websocket

import com.blikeng.chatapp.entities.InviteType
import java.time.Instant
import java.util.*

data class WsInviteReceived(
    val type: String = "INVITE_RECEIVED",
    val id: UUID,
    val inviteType: InviteType,
    val fromUserId: UUID,
    val roomId: UUID?,
    val expiresAt: Instant,
)
