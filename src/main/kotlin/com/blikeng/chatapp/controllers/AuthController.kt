package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.auth.LoginDto
import com.blikeng.chatapp.services.AuthService
import com.blikeng.chatapp.services.UserService
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// ==========================
// Exposes authentication endpoints for user registration, login,
// logout, and simple authorization checks. Sets and clears the
// AUTH cookie used for JWT-based authentication.
// ==========================
@RestController
@RequestMapping("/api")
class AuthController(
    private val authService: AuthService,
    private val userService: UserService,
    private val environment: Environment
) {
    private val maxCookieAge: Long = 24 * 60 * 60 // 24 hours

    @PostMapping("/register")
    fun register(@RequestBody loginDto: LoginDto): ResponseEntity<String> {
        val username = loginDto.username
        val password = loginDto.password

        val token = authService.registerUser(username, password)
        val cookie = makeCookie(token, maxCookieAge)

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

        val cookie = makeCookie(token, maxCookieAge)

        return ResponseEntity
            .status(200)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body("User logged in")
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<String> {
        val cookie = makeCookie("", 0)

        return ResponseEntity
            .status(200)
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body("User logged out")
    }

    @GetMapping("/auth")
    fun auth(): ResponseEntity<String> {
        return ResponseEntity.ok(userService.getSelf().username)
    }

    private fun makeCookie(token: String, maxAge: Long): ResponseCookie {
        val isProd: Boolean = environment.activeProfiles.contains("prod")

        return ResponseCookie.from("AUTH", token)
            .httpOnly(true)
            .secure(isProd)
            .path("/")
            .sameSite("Strict")
            .maxAge(maxAge)
            .build()
    }
}