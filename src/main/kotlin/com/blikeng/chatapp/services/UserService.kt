package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.auth.AuthDTO
import com.blikeng.chatapp.dtos.user.ChangeUserDTO
import com.blikeng.chatapp.dtos.user.EditPasswordDTO
import com.blikeng.chatapp.dtos.user.UserDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.InvalidFieldException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.ShortPasswordException
import com.blikeng.chatapp.errors.WrongPasswordException
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.UserRole
import com.blikeng.chatapp.security.auth.PasswordService
import com.blikeng.chatapp.security.auth.getId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.*


// ==========================
// Handles user retrieval, authenticated profile access,
// profile updates, and password changes.
// ==========================
@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordService: PasswordService,
    private val roomRepository: RoomRepository,
    private val userRevocationService: UserRevocationService,
    private val objectMapper: ObjectMapper,
) {
    fun getUserById(id: UUID): UserEntity? {
        return userRepository.findById(id).orElse(null)
    }

    fun getSelf(): UserDTO {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        return UserDTO(
            userId = id,
            username = user.username,
            bio = user.bio,
            email = user.email,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            birthday = user.birthday,
            createdAt = user.createdAt,
            rooms = roomRepository.findRoomsForUser(id)
        )
    }

    fun authenticate(): AuthDTO {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        return AuthDTO(
            userId = id,
            username = user.username,
            userRole = user.role,
        )
    }

    @Transactional
    fun editProfile(changeUserDTO: ChangeUserDTO) {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        val bio = changeUserDTO.bio.trim()
        val email = changeUserDTO.email.trim()
        val fullName = changeUserDTO.fullName.trim()
        val avatarUrl = changeUserDTO.avatarUrl.trim()

        if (bio.length > 500) throw InvalidFieldException()
        if (email.isNotBlank() && !Regex("^.+@.+$").matches(email)) throw InvalidFieldException()
        if (email.length > 254) throw InvalidFieldException()
        if (fullName.length > 100) throw InvalidFieldException()
        if (avatarUrl.length > 500) throw InvalidFieldException()

        user.bio = bio
        user.email = email
        user.fullName = fullName
        user.avatarUrl = avatarUrl
    }

    @Transactional
    fun editPassword(passwords: EditPasswordDTO) {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        if (!passwordService.checkPassword(passwords.oldPassword, user.password)) throw WrongPasswordException()
        if (passwords.newPassword.trim().length < 8) throw ShortPasswordException()

        val encoded = passwordService.encodePassword(passwords.newPassword)
        user.password = encoded
    }

    @Transactional
    fun deleteUser() {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        userRevocationService.revoke(id)
        userRepository.delete(user)
    }

    fun getAllById(userIds: List<UUID>): List<UserEntity> {
        return userRepository.findAllById(userIds)
    }
}