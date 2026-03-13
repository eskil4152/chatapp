package com.blikeng.chatapp.dtos.websocket

import java.util.*

data class ReceivedMessageDTO(val roomId: UUID, val userId: UUID, val content: String, val type: String)