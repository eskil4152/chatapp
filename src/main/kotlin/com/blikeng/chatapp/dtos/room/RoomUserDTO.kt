package com.blikeng.chatapp.dtos.room

import java.util.*

data class RoomUserDTO (
    val id: UUID,
    val username: String,
    val avatarUrl: String?,
    val online: Boolean
)