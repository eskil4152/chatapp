package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.RoomBan
import com.blikeng.chatapp.entities.RoomBanId
import com.blikeng.chatapp.repositories.RoomBanRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BannedUserService(
    val roomBanRepository: RoomBanRepository
) {
    fun isUserBanned(userId: UUID, roomId: UUID): Boolean {
        return roomBanRepository.existsById(RoomBanId(userId, roomId))
    }

    fun banUser(userId: UUID, roomId: UUID) {
        val roomBan = RoomBan(RoomBanId(userId, roomId))
        roomBanRepository.save(roomBan);
    }

    fun unbanUser(userId: UUID, roomId: UUID) {
        val id = RoomBanId(userId, roomId)
        roomBanRepository.deleteById(id)
    }

    fun getBannedUserIds(roomId: UUID): List<UUID> {
        return roomBanRepository.findAllByIdRoomId(roomId).map { it.id.userId }
    }
}