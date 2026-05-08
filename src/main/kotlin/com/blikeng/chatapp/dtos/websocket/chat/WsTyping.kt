package com.blikeng.chatapp.dtos.websocket.chat

import java.util.UUID

data class WsTyping(
    val type: String = "TYPING",
    val userId: UUID,
    val roomId: UUID,
    val username: String,
)
