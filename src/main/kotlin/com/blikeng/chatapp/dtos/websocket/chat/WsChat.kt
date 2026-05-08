package com.blikeng.chatapp.dtos.websocket.chat

import java.time.Instant
import java.util.UUID

data class WsChat(
    val type: String = "MESSAGE",
    val userId: UUID?,
    val username: String,
    val content: String,
    val timestamp: Instant,
)
