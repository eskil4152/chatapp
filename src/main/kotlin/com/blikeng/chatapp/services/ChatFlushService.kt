package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// ==========================
// Persists batches of chat messages to the database.
// Used by the RabbitMQ flush consumer during asynchronous message persistence.
// ==========================
@Service
class ChatFlushService(private val chatRepository: ChatRepository) {

    @Transactional
    fun saveBatch(batch: List<ChatEntity>) {
        chatRepository.saveAll(batch)
    }
}