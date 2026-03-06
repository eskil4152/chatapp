package com.blikeng.chatapp.services

import com.blikeng.chatapp.ErrorMessages.INVALID_PASSWORD
import com.blikeng.chatapp.ErrorMessages.INVALID_TOKEN
import com.blikeng.chatapp.ErrorMessages.INVALID_USER
import com.blikeng.chatapp.ErrorMessages.SHORT_PASSWORD
import com.blikeng.chatapp.ErrorMessages.USER_NOT_FOUND
import com.blikeng.chatapp.dtos.ChangeUserDTO
import com.blikeng.chatapp.dtos.EditPasswordDTO
import com.blikeng.chatapp.dtos.UserDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.PasswordService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.*

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
        val id = SecurityContextHolder.getContext().authentication?.principal as? UUID ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
        val user = getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, USER_NOT_FOUND)

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
        val id = SecurityContextHolder.getContext().authentication?.principal as? UUID ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
        val user = getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        changeUserDTO.bio.let { user.bio = it }
        changeUserDTO.email.let { user.email = it }
        changeUserDTO.fullName.let { user.fullName = it }
        changeUserDTO.avatarUrl.let { user.avatarUrl = it }

        userRepository.save(user)
    }

    fun editPassword(passwords: EditPasswordDTO) {
        val id = SecurityContextHolder.getContext().authentication?.principal as? UUID ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
        val user = getUserById(id) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_USER)

        if (!passwordService.checkPassword(passwords.oldPassword, user.password)) throw ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PASSWORD)
        if (passwords.newPassword.trim().length < 8) throw ResponseStatusException(HttpStatus.BAD_REQUEST, SHORT_PASSWORD)

        val encoded = passwordService.encodePassword(passwords.newPassword)
        user.password = encoded

        userRepository.save(user)
    }
}