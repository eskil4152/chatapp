package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.DTOs.LoginDto
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.security.PasswordService
import com.blikeng.chatapp.services.AuthService
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
    @Autowired private val authService: AuthService
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

    @PostMapping("/register")
    fun register(@RequestBody loginDto: LoginDto, response: HttpServletResponse): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val user = authService.registerUser(username, password) ?: return ResponseEntity.status(409)
            .body("Username already exists")

        val cookie = makeCookie(user)
        response.addCookie(cookie)

        return ResponseEntity.status(201).body("User registered successfully")
    }

    @PostMapping("/login")
    fun login(@RequestBody loginDto: LoginDto, response: HttpServletResponse): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val user =
            authService.loginUser(username, password) ?: return ResponseEntity.status(401).body("Invalid credentials")

        val cookie = makeCookie(user)
        response.addCookie(cookie)

        return ResponseEntity.ok("User logged in")
    }
}