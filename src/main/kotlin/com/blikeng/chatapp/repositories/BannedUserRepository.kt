package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.entities.BannedUserId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BannedUserRepository: JpaRepository<BannedUser, BannedUserId> {
}