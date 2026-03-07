package com.blikeng.chatapp.dtos

import com.blikeng.chatapp.repositories.JoinedRoom
import java.util.*

data class UserDTO (
    val username: String,
    val bio: String?,
    val email: String?,
    val fullName: String?,
    val avatarUrl: String?,
    val birthday: Date?,
    val createdAt: Date?,
    val rooms: List<JoinedRoom>,
    //val friends: MutableSet<UserFriendEntity>
)