package com.blikeng.chatapp.events

import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.security.UserRole
import java.util.UUID

data class UserRoleChangedEvent (
    val userId: UUID,
    val byUsername: String,
    val newRole: UserRole,
    val action: RoleAction
)