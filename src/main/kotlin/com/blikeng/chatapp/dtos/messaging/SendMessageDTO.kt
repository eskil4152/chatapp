package com.blikeng.chatapp.dtos.messaging

import java.time.Instant
import java.util.*

@Suppress("ArrayInDataClass")
data class SendMessageDTO(
    val id: UUID,
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val message: String?,
    val nonce: ByteArray?,
    val ciphertext: ByteArray?,
    val timestamp: Instant,
    val keyVersion: Int? = null
)