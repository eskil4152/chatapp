package com.blikeng.chatapp.config

import java.util.UUID

fun configureAad(roomId: UUID, chatId: UUID, userId: UUID): ByteArray {
    return "$roomId|$chatId|$userId".toByteArray(Charsets.UTF_8)
}