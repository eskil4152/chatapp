package com.blikeng.chatapp.dtos.administration

import com.blikeng.chatapp.security.UserRole
import java.time.Instant
import java.util.UUID

data class ElevatedUserDTO(
    val id: UUID,
    val username: String,
    val avatarUrl: String?,
    val role: UserRole,
    val createdAt: Instant,
)
