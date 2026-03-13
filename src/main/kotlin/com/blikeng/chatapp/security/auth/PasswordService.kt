package com.blikeng.chatapp.security.auth

import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

// ==========================
// Wraps password hashing and password verification using the configured
// Spring Security PasswordEncoder.
// ==========================
@Service
class PasswordService(private val passwordEncoder: PasswordEncoder) {

    fun encodePassword(password: String): String {
        return passwordEncoder.encode(password)!!
    }

    fun checkPassword(password: String, encoded: String): Boolean {
        return passwordEncoder.matches(password, encoded)
    }
}