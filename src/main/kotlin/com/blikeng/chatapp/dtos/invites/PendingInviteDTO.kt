package com.blikeng.chatapp.dtos.invites

import com.blikeng.chatapp.entities.InviteType
import java.time.Instant
import java.util.*

data class PendingInviteDTO(
    val id: UUID,
    val type: InviteType,
    val fromUserId: UUID,
    val roomId: UUID?,
    val expiresAt: Instant,
)