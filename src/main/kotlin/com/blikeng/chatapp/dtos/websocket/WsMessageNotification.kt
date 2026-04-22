package com.blikeng.chatapp.dtos.websocket;

import java.util.UUID;

data class WsMessageNotification (
        val type: String = "MESSAGE_NOTIFICATION",
        val roomId: UUID,
        val roomName: String,
        val username: String,
        val message: String
)
