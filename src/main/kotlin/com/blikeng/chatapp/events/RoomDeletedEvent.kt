package com.blikeng.chatapp.events

import java.util.UUID

data class RoomDeletedEvent(
    val roomId: UUID,
    val roomName: String,
    val memberIds: List<UUID>,
)
