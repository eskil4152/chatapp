package com.blikeng.chatapp.dtos.friends

import java.time.Instant
import java.util.Date
import java.util.UUID

data class FriendDTO(
    val userId: UUID?,
    val username: String?,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val friendsSince: Instant?,
)
