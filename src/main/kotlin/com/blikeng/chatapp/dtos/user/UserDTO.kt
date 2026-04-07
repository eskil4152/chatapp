package com.blikeng.chatapp.dtos.user

import com.blikeng.chatapp.dtos.room.JoinedRoomDTO
import java.time.Instant
import java.util.*

data class UserDTO (
    val userId: UUID,
    val username: String,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Instant?,
    val rooms: List<JoinedRoomDTO>,
)