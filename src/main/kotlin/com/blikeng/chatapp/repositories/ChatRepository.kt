package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.ChatEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatRepository : JpaRepository<ChatEntity, Long> {
    fun findByRoomIdOrderByTimestampDesc(
        roomId: UUID,
        pageable: Pageable,
    ): Page<ChatEntity>

    fun getAllChatsByRoomId(roomId: UUID): List<ChatEntity>
}
