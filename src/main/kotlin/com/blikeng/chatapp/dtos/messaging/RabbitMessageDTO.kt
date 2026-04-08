package com.blikeng.chatapp.dtos.messaging

import java.time.Instant
import java.util.*

@Suppress("ArrayInDataClass")
data class RabbitMessageDTO(
    val id: UUID = UUID.randomUUID(),
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val message: String? = null,
    val ciphertext: ByteArray? = null,
    val nonce: ByteArray? = null,
    val keyVersion: Int? = null,
    val timestamp: Instant = Instant.now()
)