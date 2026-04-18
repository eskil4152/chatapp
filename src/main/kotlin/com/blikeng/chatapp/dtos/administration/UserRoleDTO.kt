package com.blikeng.chatapp.dtos.administration

import com.blikeng.chatapp.dtos.room.RoleAction

data class UserRoleDTO (
    val id: String,
    val action: RoleAction
)