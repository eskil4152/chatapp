package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoomRepository: JpaRepository<RoomEntity, UUID> {
    fun save(room: RoomEntity): RoomEntity

    override fun findById(roomId: UUID): Optional<RoomEntity>

    @Query("""
    select new com.blikeng.chatapp.repositories.JoinedRoom(r, ur.role, ur.type)
    from RoomEntity r
    join UserRoomEntity ur on ur.id.roomId = r.id
    where ur.id.userId = :userId
""")
    fun findRoomsForUser(@Param("userId") userId: UUID): List<JoinedRoom>
}

data class JoinedRoom(val room: RoomEntity, val role: RoomRole, val type: RoomType)