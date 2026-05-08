package com.blikeng.chatapp.notifications.events

import com.blikeng.chatapp.dtos.room.RoomAction
import java.util.UUID

data class UserRemovedEvent(
    val targetId: UUID,
    val roomId: UUID,
    val action: RoomAction,
    val reason: String?,
)
