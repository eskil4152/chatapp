package com.blikeng.chatapp.dtos.invites

data class InviteResponseDTO(
    val inviteId: String,
    val response: InviteResponse,
)

enum class InviteResponse {
    ACCEPTED,
    REJECTED,
}
