package com.blikeng.chatapp.dtos.messaging

import java.time.Instant
import java.util.UUID

data class SendMessageDTO(
    val id: UUID,
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val message: String?,
    val timestamp: Instant,
)
