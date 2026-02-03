package com.blikeng.chatapp.dtos

import com.blikeng.chatapp.entities.RoomEntity
import java.util.*

data class UserDTO (
    val username: String,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Date?,
    val rooms: MutableSet<RoomEntity>,
    //val friends: MutableSet<UserFriendEntity>
)