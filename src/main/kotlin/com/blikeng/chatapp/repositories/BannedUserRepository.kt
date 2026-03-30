package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.entities.BannedUserId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BannedUserRepository: JpaRepository<BannedUser, BannedUserId> {
    fun findAllByIdRoomId(roomId: UUID): List<BannedUser>
}
