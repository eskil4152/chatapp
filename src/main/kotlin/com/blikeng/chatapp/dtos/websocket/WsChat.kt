package com.blikeng.chatapp.dtos.websocket

import java.sql.Timestamp

data class WsChat(
    val type: String = "MESSAGE",
    val username: String,
    val content: String,
    val timestamp: Timestamp
)