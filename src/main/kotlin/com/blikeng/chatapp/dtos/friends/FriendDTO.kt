package com.blikeng.chatapp.dtos.friends

import java.util.Date

data class FriendDTO (
    val username: String?,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Date?,
)