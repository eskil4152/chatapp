package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.RoomBan
import com.blikeng.chatapp.entities.RoomBanId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RoomBanRepository: JpaRepository<RoomBan, RoomBanId> {
    fun findAllByIdRoomId(roomId: UUID): List<RoomBan>
}
