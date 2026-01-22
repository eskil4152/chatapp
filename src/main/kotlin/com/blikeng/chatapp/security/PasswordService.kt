package com.blikeng.chatapp.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class PasswordService(private val passwordEncoder: PasswordEncoder) {

    fun encodePassword(password: String): String {
        return passwordEncoder.encode(password)!!
    }

    fun checkPassword(password: String, encoded: String): Boolean {
        return passwordEncoder.matches(password, encoded)
    }
}