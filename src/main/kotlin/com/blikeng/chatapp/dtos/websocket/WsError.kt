package com.blikeng.chatapp.dtos.websocket

data class WsError(
    val type: String = "ERROR",
    val code: Int,
    val message: String,
)
