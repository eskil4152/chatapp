package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.services.ChatFlushService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ChatFlushServiceTests {
    // ==========================
    // Tests for ChatFlushService.
    // Verifies that chat batches are persisted by delegating to ChatRepository.
    // ==========================

    private val chatRepository = mockk<ChatRepository>()
    private val service = ChatFlushService(chatRepository)

    @Test
    fun saveBatchShouldCallSaveAllOnce() {
        val batch = listOf(mockk<ChatEntity>(), mockk<ChatEntity>(), mockk<ChatEntity>())

        every { chatRepository.saveAll(any<List<ChatEntity>>()) } returns batch

        service.saveBatch(batch)

        verify(exactly = 1) { chatRepository.saveAll(batch) }
    }
}
