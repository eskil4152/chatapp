package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.entities.BannedUserId
import com.blikeng.chatapp.repositories.BannedUserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BannedUserService(
    val bannedUserRepository: BannedUserRepository
) {
    fun isUserBanned(userId: UUID, roomId: UUID): Boolean {
        return bannedUserRepository.existsById(BannedUserId(userId, roomId))
    }

    fun banUser(userId: UUID, roomId: UUID) {
        val bannedUser = BannedUser(BannedUserId(userId, roomId))
        bannedUserRepository.save(bannedUser);
    }

    fun unbanUser(userId: UUID, roomId: UUID) {
        val id = BannedUserId(userId, roomId)
        bannedUserRepository.deleteById(id)
    }

    fun getBannedUserIds(roomId: UUID): List<UUID> {
        return bannedUserRepository.findAllByIdRoomId(roomId).map { it.id.userId }
    }
}