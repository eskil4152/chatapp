package com.blikeng.chatapp.dtos.room

import com.blikeng.chatapp.entities.RoomRole
import java.util.UUID

data class RoomUserDTO(
    val id: UUID,
    val username: String,
    val avatarUrl: String?,
    val online: Boolean,
    val role: RoomRole? = null,
)
