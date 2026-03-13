package com.blikeng.chatapp.config

import java.util.*

// ==========================
// Builds additional authenticated data (AAD) for chat message encryption.
// Binds ciphertext to a specific room, message, and sender.
// ==========================
fun configureAad(roomId: UUID, chatId: UUID, userId: UUID): ByteArray {
    return "$roomId|$chatId|$userId".toByteArray(Charsets.UTF_8)
}