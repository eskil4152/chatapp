package com.blikeng.chatapp.dtos.administration

import com.blikeng.chatapp.dtos.room.JoinedRoomDTO
import com.blikeng.chatapp.security.UserRole
import java.util.UUID
import java.time.Instant

data class ElevatedUserDTO (
    val id: UUID,
    val username: String,
    val avatarUrl: String?,
    val role: UserRole,
    val createdAt: Instant,
)