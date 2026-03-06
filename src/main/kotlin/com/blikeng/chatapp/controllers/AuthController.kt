package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.LoginDto
import com.blikeng.chatapp.services.AuthService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api")
class AuthController(
    @Autowired private val authService: AuthService
) {
    @PostMapping("/register")
    fun register(@RequestBody loginDto: LoginDto): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val token = authService.registerUser(username, password)
        val cookie = ResponseCookie.from("AUTH", token)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(24 * 60 * 60)
            .build()

        return ResponseEntity
            .status(201)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body("User registered successfully")
    }

    @PostMapping("/login")
    fun login(@RequestBody loginDto: LoginDto): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val token = authService.loginUser(username, password)
        val cookie = ResponseCookie.from("AUTH", token)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .sameSite("None")
            .maxAge(24 * 60 * 60)
            .build()


        return ResponseEntity
            .status(200)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body("User logged in")
    }

    @GetMapping("/auth")
    fun auth(): ResponseEntity<String> {
        return ResponseEntity.ok("Authorized")
    }
}