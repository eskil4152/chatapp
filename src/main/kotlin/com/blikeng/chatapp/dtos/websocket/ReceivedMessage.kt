package com.blikeng.chatapp.dtos.websocket

import java.util.*

data class ReceivedMessage(val roomId: UUID, val userId: UUID, val content: String, val type: String)