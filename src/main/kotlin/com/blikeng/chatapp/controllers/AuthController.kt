package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.security.PasswordService
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    @Autowired private val jwtService: JwtService,
    @Autowired private val passwordService: PasswordService
) {
    @PostMapping("/register")
    fun register(@RequestBody loginDto: LoginDto, response: HttpServletResponse): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password
        val encryptedPassword = passwordService.encodePassword(password)

        val token = jwtService.generateToken(username)

        // TODO: Register the user. Make sure username is free as well.

        val cookie = Cookie("AUTH", token).apply {
            path = "/"
            secure = false
            isHttpOnly = true
            maxAge = 24 * 60 * 60
        }

        response.addCookie(cookie)

        return ResponseEntity.ok("User registered successfully")
    }

    @PostMapping("/login")
    fun login(@RequestBody loginDto: LoginDto): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        // TODO: Allow log-in. First finish register and persistence.

        return ResponseEntity.ok("User logged in")
    }
}

data class LoginDto(val username: String, val password: String)