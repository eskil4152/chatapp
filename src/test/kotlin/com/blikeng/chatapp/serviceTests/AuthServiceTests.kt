package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.AuthRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.security.PasswordService
import com.blikeng.chatapp.services.AuthService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class AuthServiceTests {
    @MockK private lateinit var authRepository: AuthRepository
    @MockK private lateinit var passwordService: PasswordService
    @MockK private lateinit var jwtService: JwtService

    @InjectMockKs
    lateinit var authService: AuthService

    @Test
    fun shouldRegisterUser() {
        every { authRepository.existsByUsername(any()) } returns false
        every { passwordService.encodePassword("p") } returns "ENC"
        every { authRepository.save(any()) } answers { firstArg() }
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val cookie = authService.registerUser("u", "p")
        assert(cookie.name == "AUTH")
    }

    @Test
    fun shouldNotRegisterUserWithExistingUsername() {
        every { authRepository.existsByUsername(any()) } returns true
        every { passwordService.encodePassword(any()) } returns "ENCO"

        val exception = assertFailsWith<ResponseStatusException> {
            authService.registerUser("u", "p")
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        assertEquals("Username already exists", exception.reason)
    }

    @Test
    fun shouldFailLoginOnNonExistingUser(){
        every { authRepository.findByUsername(any()) } returns null

       val exception = assertFailsWith<ResponseStatusException> {
           authService.loginUser("u", "p")
       }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid credentials", exception.reason)
    }

    @Test
    fun shouldFailLoginOnWrongPassword(){
        every { authRepository.findByUsername(any()) } returns UserEntity(username = "u", password = "")
        every { passwordService.checkPassword("p", any()) } returns false

        val exception = assertFailsWith<ResponseStatusException> {
            authService.loginUser("u", "p")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid credentials", exception.reason)
    }

    @Test
    fun shouldLoginSuccessfully(){
        every { authRepository.findByUsername(any()) } returns UserEntity(username = "u", password = "")
        every { passwordService.checkPassword("p", any()) } returns true
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val res = authService.loginUser("u", "p")
        assert(res.name == "AUTH")
    }
}