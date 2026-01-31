package com.blikeng.chatapp.DTOs

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserFriendEntity
import java.util.Date

class ChangeUserDTO (
    val bio: String,
    val email: String,
    val fullName: String,
    val avatarUrl: String,
)