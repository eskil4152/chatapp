package com.blikeng.chatapp.config

import java.util.UUID

// ==========================
// Builds additional authenticated data (AAD) for chat message encryption.
// Binds ciphertext to a specific room, message, and sender.
// ==========================
fun configureAad(
    roomId: UUID,
    chatId: UUID,
    userId: UUID,
): ByteArray = "$roomId|$chatId|$userId".toByteArray(Charsets.UTF_8)
