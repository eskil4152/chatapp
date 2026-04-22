package com.blikeng.chatapp.dtos.auth

import com.blikeng.chatapp.security.UserRole
import java.util.UUID

data class AuthDTO (
    val userId: UUID,
    val username: String,
    val userRole: UserRole
)