package com.blikeng.chatapp.dtos.messaging

import java.sql.Timestamp
import java.util.UUID

@Suppress("ArrayInDataClass")
data class SendMessageDTO(
    val id: UUID,
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val message: String?,
    val nonce: ByteArray?,
    val ciphertext: ByteArray?,
    val timestamp: Timestamp,
    val keyVersion: Int? = null
)