package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.security.PasswordService
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.Test

@ExtendWith(MockKExtension::class)
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