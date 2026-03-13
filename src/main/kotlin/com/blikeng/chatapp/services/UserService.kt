package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.ChangeUserDTO
import com.blikeng.chatapp.dtos.EditPasswordDTO
import com.blikeng.chatapp.dtos.UserDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.ShortPasswordException
import com.blikeng.chatapp.errors.WrongPasswordException
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.auth.PasswordService
import com.blikeng.chatapp.tools.getId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

// ==========================
// Handles user retrieval, authenticated profile access,
// profile updates, and password changes.
// ==========================
@Service
class UserService(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordService: PasswordService,
    @Autowired private val roomRepository: RoomRepository,
) {
    fun getUserById(id: UUID): UserEntity? {
        return userRepository.findById(id).orElse(null)
    }

    fun getSelf(): UserDTO {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        return UserDTO(
            user.username,
            bio = user.bio,
            email = user.email,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            birthday = user.birthday,
            createdAt = user.createdAt,
            rooms = roomRepository.findRoomsForUser(id)
        )
    }

    fun editProfile(changeUserDTO: ChangeUserDTO) {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        changeUserDTO.bio.let { user.bio = it }
        changeUserDTO.email.let { user.email = it }
        changeUserDTO.fullName.let { user.fullName = it }
        changeUserDTO.avatarUrl.let { user.avatarUrl = it }

        userRepository.save(user)
    }

    fun editPassword(passwords: EditPasswordDTO) {
        val id = getId()
        val user = getUserById(id) ?: throw InvalidUserException()

        if (!passwordService.checkPassword(passwords.oldPassword, user.password)) throw WrongPasswordException()
        if (passwords.newPassword.trim().length < 8) throw ShortPasswordException()

        val encoded = passwordService.encodePassword(passwords.newPassword)
        user.password = encoded

        userRepository.save(user)
    }
}