package com.blikeng.chatapp.events

import com.blikeng.chatapp.entities.InviteType
import java.util.*

data class InviteAcceptedEvent(
    val fromUserId: UUID,
    val fromUsername: String,
    val fromAvatarUrl: String?,
    val toUserId: UUID,
    val toUsername: String,
    val toAvatarUrl: String?,
    val type: InviteType,
    val roomId: UUID?,
)
