package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.AuthRepository
import com.blikeng.chatapp.security.PasswordService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AuthService(
    @Autowired private val passwordService: PasswordService,
    @Autowired private val authRepository: AuthRepository
) {
    fun registerUser(username: String, password: String): UserEntity? {
        val exists = authRepository.existsByUsername(username)
        if (exists) return null

        val encPassword = passwordService.encodePassword(password)

        val user = UserEntity(username = username, password = encPassword)

        return authRepository.save(user)
    }

    fun loginUser(username: String, password: String): UserEntity? {
        val user = authRepository.findByUsername(username)
        if (user.isEmpty) {
            return null
        }

        val isValid = passwordService.checkPassword(password, user.get().password)

        return if (isValid) user.get() else null
    }
}