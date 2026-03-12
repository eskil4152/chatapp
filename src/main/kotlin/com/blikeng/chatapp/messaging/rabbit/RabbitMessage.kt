package com.blikeng.chatapp.messaging.rabbit

import java.sql.Timestamp
import java.util.UUID

data class RabbitMessage(
    val id: UUID = UUID.randomUUID(),
    val roomId: UUID,
    val userId: UUID,
    val username: String,
    val message: String? = null,
    val ciphertext: ByteArray? = null,
    val nonce: ByteArray? = null,
    val keyVersion: Int? = null,
    val timestamp: Timestamp = Timestamp(System.currentTimeMillis())
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RabbitMessage

        if (keyVersion != other.keyVersion) return false
        if (id != other.id) return false
        if (roomId != other.roomId) return false
        if (userId != other.userId) return false
        if (message != other.message) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (!nonce.contentEquals(other.nonce)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyVersion ?: 0
        result = 31 * result + id.hashCode()
        result = 31 * result + roomId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + (message?.hashCode() ?: 0)
        result = 31 * result + (ciphertext?.contentHashCode() ?: 0)
        result = 31 * result + (nonce?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}