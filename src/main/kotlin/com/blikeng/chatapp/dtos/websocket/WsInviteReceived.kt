package com.blikeng.chatapp.dtos.websocket

import com.blikeng.chatapp.entities.InviteType
import java.time.Instant
import java.util.*

data class WsInviteReceived(
    val type: String = "INVITE_RECEIVED",
    val id: UUID,
    val inviteType: InviteType,
    val fromUsername: String,
    val roomName: String?,
    val fromAvatarUrl: String?,
)
