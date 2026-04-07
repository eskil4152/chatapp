package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.InvalidCredentialsException
import com.blikeng.chatapp.errors.LongPasswordException
import com.blikeng.chatapp.errors.LongUsernameException
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
    private val passwordService: PasswordService,
    private val jwtService: JwtService,
    private val authRepository: AuthRepository,
) {
    fun registerUser(username: String, password: String): String {
        val trimmedUsername = username.trim()
        val trimmedPassword = password.trim()

        if (trimmedUsername.length < 3) throw ShortUsernameException()
        if (trimmedUsername.length > 32) throw LongUsernameException()
        if (trimmedPassword.length < 8) throw ShortPasswordException()
        if (trimmedPassword.length > 128) throw LongPasswordException()
        if (authRepository.existsByUsernameIgnoreCase(trimmedUsername)) throw UsernameAlreadyExistsException()

        val user = authRepository.save(UserEntity(username = trimmedUsername, password = passwordService.encodePassword(trimmedPassword)))

        return jwtService.generateToken(user)
    }

    fun loginUser(username: String, password: String): String {
        val user = authRepository.findByUsernameIgnoreCase(username) ?: throw InvalidCredentialsException()
        if (!passwordService.checkPassword(password, user.password)) throw InvalidCredentialsException()

        return jwtService.generateToken(user)
    }
}