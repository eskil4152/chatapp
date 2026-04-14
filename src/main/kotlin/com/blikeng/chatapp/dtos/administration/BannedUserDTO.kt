package com.blikeng.chatapp.dtos.administration

import com.blikeng.chatapp.security.UserRole
import java.time.Instant
import java.util.UUID

data class BannedUserDTO(
    val userId: UUID,
    val username: String,
    val bannedBy: UUID,
    val bannedByUsername: String,
    val bannedByRole: UserRole,
    val bannedAt: Instant,
    val reason: String?
)