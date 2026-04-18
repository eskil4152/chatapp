package com.blikeng.chatapp.dtos.administration

import com.blikeng.chatapp.dtos.room.JoinedRoomDTO
import com.blikeng.chatapp.security.UserRole
import java.time.Instant
import java.util.UUID

data class UserDetailDTO (
    val id: UUID,
    val username: String,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val role: UserRole,
    val createdAt: Instant,
    val rooms: List<JoinedRoomDTO>?,
)