package com.blikeng.chatapp.dtos.websocket

import java.util.UUID

data class ReceivedMessage(
    val roomId: UUID,
    val userId: UUID,
    val content: String,
    val type: String,
)
