package com.blikeng.chatapp.dtos.websocket

import java.util.*

data class WsJoined(
    val type: String = "JOINED",
    val roomId: UUID,
    val roomName: String,
    val encrypted: Boolean,
)

