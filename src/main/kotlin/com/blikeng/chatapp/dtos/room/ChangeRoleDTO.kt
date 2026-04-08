package com.blikeng.chatapp.dtos.room

data class ChangeRoleDTO(
    val userId: String,
    val roomId: String,
    val action: RoleAction,
)

enum class RoleAction {
    PROMOTE,
    DEMOTE,
}
