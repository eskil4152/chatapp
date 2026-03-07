package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.security.PasswordService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import kotlin.test.Test

class PasswordServiceTest {
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