package com.blikeng.chatapp.DTOs

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserFriendEntity
import java.util.Date

class UserDTO (
    val username: String,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Date?,
    val rooms: MutableSet<RoomEntity>,
    val friends: MutableSet<UserFriendEntity>
)