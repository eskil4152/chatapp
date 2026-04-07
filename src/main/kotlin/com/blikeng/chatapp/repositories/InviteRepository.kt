package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.InviteEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface InviteRepository: JpaRepository<InviteEntity, UUID> {
    @Query("""
        SELECT COUNT(i) > 0 FROM InviteEntity i
        WHERE i.type = 'FRIEND_REQUEST'
          AND i.status = 'PENDING'
          AND (
                (i.fromUserId = :id AND i.toUserId = :friendId)
             OR (i.fromUserId = :friendId AND i.toUserId = :id)
          )
    """)
    fun existsPendingFriendRequest(id: UUID, friendId: UUID): Boolean
}