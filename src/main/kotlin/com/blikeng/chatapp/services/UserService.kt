package com.blikeng.chatapp.services

import com.blikeng.chatapp.DTOs.UserDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtService: JwtService
) {
    fun getUserById(id: UUID): UserEntity? {
        return userRepository.findById(id).orElse(null)
    }

    fun getSelf(token: String): UserDTO? {
        val userId = jwtService.validateToken(token) ?: return null
        val user = getUserById(userId) ?: return null

        return UserDTO(
            user.username,
            bio = user.bio,
            email = user.email,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            birthday = user.birthday,
            createdAt = user.createdAt,
            rooms = user.rooms,
            friends = user.friends
        )
    }
}