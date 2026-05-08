package com.blikeng.chatapp.dtos.websocket

import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.security.UserRole
import java.util.UUID

data class WsUserRoleChanged(
    val type: String = "USER_ROLE_CHANGED",
    val userId: UUID,
    val byUsername: String,
    val newRole: UserRole,
    val action: RoleAction,
)
