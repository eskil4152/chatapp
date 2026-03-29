package com.blikeng.chatapp.dtos.friends

import java.util.*

data class FriendDTO (
    val userId: UUID?,
    val username: String?,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Date?,
    val online: Boolean?,
)
