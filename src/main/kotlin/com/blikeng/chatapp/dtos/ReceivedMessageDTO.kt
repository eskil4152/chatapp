package com.blikeng.chatapp.dtos

import java.util.UUID

data class ReceivedMessageDTO(val roomId: UUID, val userId: UUID, val content: String, val type: String)