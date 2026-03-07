package com.blikeng.chatapp.dtos

import java.util.*

data class WsJoined(
    val type: String = "JOINED",
    val roomId: UUID,
    val roomName: String,
    val encrypted: Boolean
)

data class WsError(
    val type: String = "ERROR",
    val code: Int,
    val message: String
)

data class WsChat(
    val type: String = "MESSAGE",
    val username: String,
    val content: String
)