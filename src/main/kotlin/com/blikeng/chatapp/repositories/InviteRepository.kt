package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.InviteEntity
import com.blikeng.chatapp.entities.InviteStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

    @Query("""
        SELECT COUNT(i) > 0 FROM InviteEntity i
        WHERE i.type = 'ROOM_INVITE'
        AND i.status = 'PENDING'
        AND i.roomId = :roomId
        AND i.toUserId = :id
    """)
    fun existsPendingRoomInvite(id: UUID, roomId: UUID): Boolean

    fun findByToUserIdAndStatus(toUserId: UUID, status: InviteStatus): List<InviteEntity>

    fun deleteByToUserIdAndRoomId(id: UUID, roomId: UUID)

    @Modifying
    @Query("UPDATE InviteEntity i SET i.usages = i.usages + 1 WHERE i.id = :id AND i.usages < i.maxUsages")
    fun incrementUsagesIfAvailable(id: UUID): Int
}