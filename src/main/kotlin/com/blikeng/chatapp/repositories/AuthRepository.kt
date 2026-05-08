package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AuthRepository : JpaRepository<UserEntity, UUID> {
    fun findByUsernameIgnoreCase(username: String): UserEntity?

    fun existsByUsernameIgnoreCase(username: String): Boolean

    fun save(user: UserEntity): UserEntity
}
