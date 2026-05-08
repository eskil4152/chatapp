package com.blikeng.chatapp.dtos.websocket.invites

import com.blikeng.chatapp.entities.InviteType
import java.util.UUID

data class WsInviteAccepted(
    val type: String = "INVITE_ACCEPTED",
    val inviteType: InviteType,
    val roomId: UUID?,
    val username: String,
    val avatarUrl: String?,
)
