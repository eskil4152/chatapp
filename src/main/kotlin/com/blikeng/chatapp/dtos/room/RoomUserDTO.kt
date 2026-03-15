package com.blikeng.chatapp.dtos.room

import java.util.UUID

data class RoomUserDTO (
    val id: UUID,
    val username: String,
    val avatarUrl: String?,
    val online: Boolean
)