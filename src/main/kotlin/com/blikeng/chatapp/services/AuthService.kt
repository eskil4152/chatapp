package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.InvalidCredentialsException
import com.blikeng.chatapp.errors.ShortPasswordException
import com.blikeng.chatapp.errors.ShortUsernameException
import com.blikeng.chatapp.errors.UsernameAlreadyExistsException
import com.blikeng.chatapp.repositories.AuthRepository
import com.blikeng.chatapp.security.auth.JwtService
import com.blikeng.chatapp.security.auth.PasswordService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

// ==========================
// Handles user registration and login.
// Validates credentials, hashes passwords, persists users,
// and issues JWT tokens for authenticated sessions.
// ==========================
@Service
class AuthService(
    @Autowired private val passwordService: PasswordService,
    @Autowired private val jwtService: JwtService,
    @Autowired private val authRepository: AuthRepository,
) {
    fun registerUser(username: String, password: String): String {
        if (authRepository.existsByUsername(username)) throw UsernameAlreadyExistsException()
        if (password.trim().length < 8) throw ShortPasswordException()
        if (username.trim().length < 3) throw ShortUsernameException()

        val user = authRepository.save(UserEntity(username = username, password = passwordService.encodePassword(password)))

        return jwtService.generateToken(user)
    }

    fun loginUser(username: String, password: String): String {
        val user = authRepository.findByUsername(username) ?: throw InvalidCredentialsException()
        if (!passwordService.checkPassword(password, user.password)) throw InvalidCredentialsException()

        return jwtService.generateToken(user)
    }
}