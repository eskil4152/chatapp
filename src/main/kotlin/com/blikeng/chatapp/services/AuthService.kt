package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.AuthRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.security.PasswordService
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AuthService(
    @Autowired private val passwordService: PasswordService,
    @Autowired private val jwtService: JwtService,
    @Autowired private val authRepository: AuthRepository,
) {
    fun makeCookie(user: UserEntity): Cookie {
        val token = jwtService.generateToken(user)

        val cookie = Cookie("AUTH", token).apply {
            path = "/"
            secure = false
            isHttpOnly = true
            maxAge = 24 * 60 * 60
        }

        return cookie
    }

    fun registerUser(username: String, password: String): Cookie {
        if (authRepository.existsByUsername(username)) throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")

        val user = authRepository.save(UserEntity(username = username, password = passwordService.encodePassword(password)))
        return makeCookie(user)
    }

    fun loginUser(username: String, password: String): Cookie {
        val user = authRepository.findByUsername(username) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        if (!passwordService.checkPassword(password, user.password)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        return makeCookie(user)
    }
}