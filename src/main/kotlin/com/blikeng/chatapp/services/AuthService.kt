package com.blikeng.chatapp.services

import com.blikeng.chatapp.ErrorMessages.INVALID_CREDENTIALS
import com.blikeng.chatapp.ErrorMessages.INVALID_TOKEN
import com.blikeng.chatapp.ErrorMessages.SHORT_PASSWORD
import com.blikeng.chatapp.ErrorMessages.SHORT_USERNAME
import com.blikeng.chatapp.ErrorMessages.USERNAME_ALREADY_EXISTS
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
        if (authRepository.existsByUsername(username)) throw ResponseStatusException(HttpStatus.CONFLICT, USERNAME_ALREADY_EXISTS)
        if (password.trim().length < 8) throw ResponseStatusException(HttpStatus.BAD_REQUEST, SHORT_PASSWORD)
        if (username.trim().length < 3) throw ResponseStatusException(HttpStatus.BAD_REQUEST, SHORT_USERNAME)

        val user = authRepository.save(UserEntity(username = username, password = passwordService.encodePassword(password)))

        return jwtService.generateToken(user)
    }

    fun loginUser(username: String, password: String): String {
        val user = authRepository.findByUsername(username) ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS)
        if (!passwordService.checkPassword(password, user.password)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS)

        return jwtService.generateToken(user)
    }
}