package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.RoomEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RoomRepository: JpaRepository<RoomEntity, UUID> {
    fun save(room: RoomEntity): RoomEntity

    override fun findById(roomId: UUID): Optional<RoomEntity>
}