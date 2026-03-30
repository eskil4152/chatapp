package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRoomRepository: JpaRepository<UserRoomEntity, UserRoomId> {
    fun existsByIdUserIdAndIdRoomId(userId: UUID, roomId: UUID): Boolean

    fun findByIdUserIdAndIdRoomId(userId: UUID, roomId: UUID): UserRoomEntity?

    fun deleteByIdUserIdAndIdRoomId(userId: UUID, roomId: UUID)

    @Query("SELECT u FROM UserEntity u JOIN UserRoomEntity ur ON u.id = ur.id.userId WHERE ur.id.roomId = :roomId")
    fun findUsersByRoomId(roomId: UUID): List<UserEntity>

    // Finds the other participant in a private room by excluding the current user.
    @Query("""
    SELECT u
    FROM UserEntity u
    WHERE u.id = (
        SELECT ur.id.userId
        FROM UserRoomEntity ur
        WHERE ur.id.roomId = :roomId
        AND ur.id.userId <> :userId
    )
""")
    fun findOtherUser(roomId: UUID, userId: UUID): UserEntity?

    @Query("""
        SELECT ur.id.roomId 
        FROM UserRoomEntity ur 
        WHERE ur.id.userId = :userId""")
    fun findAllIdRoomIdsByIdUserId(userId: UUID): List<UUID>
}