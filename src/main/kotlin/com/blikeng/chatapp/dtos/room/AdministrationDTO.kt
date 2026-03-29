package com.blikeng.chatapp.dtos.room;

data class AdministrationDTO (
    val roomId: String,
    val userId: String,
    val actions: RoomAction,
    val reason: String
)

enum class RoomAction {
    KICK,
    BAN
}
