package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ChatFlushService(private val chatRepository: ChatRepository) {

    @Transactional
    fun saveBatch(batch: List<ChatEntity>) {
        chatRepository.saveAll(batch)
    }
}