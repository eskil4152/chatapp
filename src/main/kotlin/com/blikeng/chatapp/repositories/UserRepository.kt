package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository: JpaRepository<UserEntity, UUID> {
    fun findByUsername(username: String): UserEntity?
}