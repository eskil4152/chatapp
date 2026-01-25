package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AuthRepository: JpaRepository<UserEntity, UUID> {
    fun findByUsername(username: String): Optional<UserEntity>

    fun existsByUsername(username: String): Boolean

    fun save(user: UserEntity): UserEntity
}