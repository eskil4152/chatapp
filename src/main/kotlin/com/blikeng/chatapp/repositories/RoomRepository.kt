package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.dtos.room.JoinedRoomDTO
import com.blikeng.chatapp.entities.RoomEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoomRepository: JpaRepository<RoomEntity, UUID> {
    fun save(room: RoomEntity): RoomEntity

    override fun findById(roomId: UUID): Optional<RoomEntity>

    // Retrieves all rooms a user is a member of together with the user's role
    // and room type, returned as a JoinedRoomDTO projection.
    @Query("""
    select new com.blikeng.chatapp.dtos.room.JoinedRoomDTO(r, ur.role, ur.type)
    from RoomEntity r
    join UserRoomEntity ur on ur.id.roomId = r.id
    where ur.id.userId = :userId
""")
    fun findRoomsForUser(@Param("userId") userId: UUID): List<JoinedRoomDTO>
}

