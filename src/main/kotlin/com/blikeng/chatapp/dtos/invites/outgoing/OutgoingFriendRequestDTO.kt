package com.blikeng.chatapp.dtos.invites.outgoing

import com.blikeng.chatapp.entities.InviteType
import java.time.Instant
import java.util.UUID

data class OutgoingFriendRequestDTO(
    override val id: UUID,
    override val type: InviteType,
    override val fromUserId: UUID,
    override val expiresAt: Instant,
    val toUserId: UUID,
    val toUsername: String,
    val avatar: String?,
) : OutgoingInvitationDTO()
