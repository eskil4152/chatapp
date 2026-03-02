package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.AuthRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.security.PasswordService
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
    fun registerUser(username: String, password: String): String {
        if (authRepository.existsByUsername(username)) throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")

        val user = authRepository.save(UserEntity(username = username, password = passwordService.encodePassword(password)))

        return jwtService.generateToken(user)
    }

    fun loginUser(username: String, password: String): String {
        val user = authRepository.findByUsername(username) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        if (!passwordService.checkPassword(password, user.password)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        return jwtService.generateToken(user)
    }

    fun validateUser(authCookie: String?){
        if (authCookie == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")

        if (jwtService.validateToken(authCookie) == null) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
    }
}