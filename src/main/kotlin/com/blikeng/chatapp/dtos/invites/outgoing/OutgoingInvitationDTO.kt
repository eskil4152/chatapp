package com.blikeng.chatapp.dtos.invites.outgoing

import com.blikeng.chatapp.entities.InviteType
import java.time.Instant
import java.util.UUID

sealed class OutgoingInvitationDTO {
    abstract val id: UUID
    abstract val type: InviteType
    abstract val fromUserId: UUID
    abstract val expiresAt: Instant
}