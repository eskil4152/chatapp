package com.blikeng.chatapp.dtos.user

import com.blikeng.chatapp.dtos.room.JoinedRoomDTO
import java.util.*

data class UserDTO (
    val username: String,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Date?,
    val rooms: List<JoinedRoomDTO>,
)