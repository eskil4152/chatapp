package com.blikeng.chatapp.events

import com.blikeng.chatapp.dtos.invites.PendingInviteDTO
import java.util.*

data class InviteSentEvent(
    val toUserId: UUID,
    val invite: PendingInviteDTO,
)
