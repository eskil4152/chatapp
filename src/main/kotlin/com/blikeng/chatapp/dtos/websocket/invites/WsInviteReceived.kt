package com.blikeng.chatapp.dtos.websocket.invites

import com.blikeng.chatapp.entities.InviteType
import java.util.UUID

data class WsInviteReceived(
    val type: String = "INVITE_RECEIVED",
    val id: UUID,
    val inviteType: InviteType,
    val fromUsername: String,
    val roomName: String?,
    val fromAvatarUrl: String?,
)
