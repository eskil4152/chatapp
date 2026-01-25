package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface RoomRepository: JpaRepository<RoomEntity, UUID> {
    fun save(room: RoomEntity): RoomEntity

    override fun findById(roomId: UUID): Optional<RoomEntity>
}