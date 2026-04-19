package com.blikeng.chatapp.securityTests.auth

import com.blikeng.chatapp.security.auth.PasswordService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.junit.jupiter.api.Test

class PasswordServiceTest {
    // ==========================
    // PasswordService tests.
    // Verifies:
    // - That password encoding works, and that the checkPassword method works.
    // - That the checkPassword method fails when the password is incorrect.
    // ==========================
    private val passwordEncoder = BCryptPasswordEncoder()
    private val passwordService = PasswordService(passwordEncoder)

    @Test
    fun shouldEncodePassword() {
        val password = "Hello!"
        val hashed = passwordService.encodePassword(password)

        assert(hashed != password)
        assert(passwordService.checkPassword(password, hashed))
    }

    @Test
    fun shouldCheckPasswordAndFail() {
        val password = "Hello!"
        val hashed = passwordService.encodePassword("Wrong!")

        assert(!passwordService.checkPassword(password, hashed))
    }
}