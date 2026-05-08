package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.security.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserRepository : JpaRepository<UserEntity, UUID> {
    fun getUserByUsernameIgnoreCase(username: String): UserEntity?

    fun findAllByRoleNot(role: UserRole): List<UserEntity>

    fun findByUsername(username: String): Optional<UserEntity>
}
