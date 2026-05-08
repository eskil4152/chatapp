package com.blikeng.chatapp.notifications.events

import com.blikeng.chatapp.dtos.invites.PendingInviteDTO
import java.util.UUID

data class InviteSentEvent(
    val toUserId: UUID,
    val invite: PendingInviteDTO,
)
