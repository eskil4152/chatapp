package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRoomRepository: JpaRepository<UserRoomEntity, UserRoomId> {
    fun existsByIdUserIdAndIdRoomId(userId: UUID, roomId: UUID): Boolean

    fun findByIdUserIdAndIdRoomId(userId: UUID, roomId: UUID): UserRoomEntity?

    fun deleteByIdUserIdAndIdRoomId(userId: UUID, roomId: UUID)

    fun deleteAllByIdRoomId(roomId: UUID)

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
}