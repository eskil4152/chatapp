package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.LoginDto
import com.blikeng.chatapp.services.AuthService
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
    @Autowired private val authService: AuthService
) {
    @PostMapping("/register")
    fun register(@RequestBody loginDto: LoginDto, response: HttpServletResponse): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val cookie = authService.registerUser(username, password)
        response.addCookie(cookie)

        return ResponseEntity.status(201).body("User registered successfully")
    }

    @PostMapping("/login")
    fun login(@RequestBody loginDto: LoginDto, response: HttpServletResponse): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val cookie = authService.loginUser(username, password)
        response.addCookie(cookie)

        return ResponseEntity.ok("User logged in")
    }
}