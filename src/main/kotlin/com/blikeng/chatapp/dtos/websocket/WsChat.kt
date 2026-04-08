package com.blikeng.chatapp.dtos.websocket

import java.time.Instant

data class WsChat(
    val type: String = "MESSAGE",
    val username: String,
    val content: String,
    val timestamp: Instant
)