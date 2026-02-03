package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.ChangeUserDTO
import com.blikeng.chatapp.dtos.UserDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.JwtService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

@Service
class UserService(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val jwtService: JwtService
) {
    fun getUserById(id: UUID): UserEntity? {
        return userRepository.findById(id).orElse(null)
    }

    fun getSelf(token: String): UserDTO {
        val (_, userId ) = jwtService.validateToken(token) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        val user = getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found")

        return UserDTO(
            user.username,
            bio = user.bio,
            email = user.email,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            birthday = user.birthday,
            createdAt = user.createdAt,
            rooms = user.rooms,
        )
    }

    fun editProfile(changeUserDTO: ChangeUserDTO, authCookie: String) {
        val (_, userId ) = jwtService.validateToken(authCookie) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        val user = getUserById(userId) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid user")

        changeUserDTO.bio.let { user.bio = it }
        changeUserDTO.email.let { user.email = it }
        changeUserDTO.fullName.let { user.fullName = it }
        changeUserDTO.avatarUrl.let { user.avatarUrl = it }

        userRepository.save(user)
    }
}