package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.repositories.AuthRepository
import com.blikeng.chatapp.security.auth.JwtService
import com.blikeng.chatapp.security.auth.PasswordService
import com.blikeng.chatapp.services.AuthService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows

@ExtendWith(MockKExtension::class)
class AuthServiceTests {
    // ==========================
    // Tests for AuthService. Verifies:
    // - User registration
    // - Registration failure cases
    // - User login
    // - Login failure cases
    // ==========================
    @MockK private lateinit var authRepository: AuthRepository
    @MockK private lateinit var passwordService: PasswordService
    @MockK private lateinit var jwtService: JwtService

    @InjectMockKs
    lateinit var authService: AuthService

    @Test
    fun shouldRegisterUser() {
        every { authRepository.existsByUsernameIgnoreCase(any()) } returns false
        every { passwordService.encodePassword("password") } returns "ENC"
        every { authRepository.save(any()) } answers { firstArg() }
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val cookie = authService.registerUser("username", "password")
        assert(cookie == "TOKEN")
    }

    @Test
    fun shouldNotRegisterUserWithExistingUsername() {
        every { authRepository.existsByUsernameIgnoreCase(any()) } returns true

        val exception = assertThrows<ApiException> {
            authService.registerUser("existinguser", "password123")
        }

        assertEquals(HttpStatus.CONFLICT, exception.status)
        assertEquals(ErrorMessages.USERNAME_EXISTS, exception.message)
    }

    @Test
    fun shouldNotRegisterUserWithTooShortUsername() {
        every { authRepository.existsByUsernameIgnoreCase(any()) } returns false

        val exception = assertThrows<ApiException> {
            authService.registerUser("u", "password")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.SHORT_USERNAME, exception.message)
    }

    @Test
    fun shouldNotRegisterUserWithTooShortPassword() {
        every { authRepository.existsByUsernameIgnoreCase(any()) } returns false

        val exception = assertThrows<ApiException> {
            authService.registerUser("username", "p")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.SHORT_PASSWORD, exception.message)
    }

    @Test
    fun shouldNotRegisterUserWithTooLongUsername() {
        val exception = assertThrows<ApiException> {
            authService.registerUser("a".repeat(33), "password123")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.LONG_USERNAME, exception.message)
    }

    @Test
    fun shouldNotRegisterUserWithTooLongPassword() {
        val exception = assertThrows<ApiException> {
            authService.registerUser("username", "p".repeat(129))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.LONG_PASSWORD, exception.message)
    }

    @Test
    fun shouldRegisterUserWithTrimmedUsername() {
        every { authRepository.existsByUsernameIgnoreCase("username") } returns false
        every { passwordService.encodePassword(any()) } returns "ENC"
        every { authRepository.save(match { it.username == "username" }) } answers { firstArg() }
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val token = authService.registerUser("  username  ", "password123")
        assertEquals("TOKEN", token)
    }

    @Test
    fun shouldLoginSuccessfully(){
        every { authRepository.findByUsernameIgnoreCase(any()) } returns UserEntity(username = "u", password = "")
        every { passwordService.checkPassword("p", any()) } returns true
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val res = authService.loginUser("u", "p")
        assert(res == "TOKEN")
    }

    @Test
    fun shouldFailLoginOnNonExistingUser(){
        every { authRepository.findByUsernameIgnoreCase(any()) } returns null

       val exception = assertThrows<ApiException> {
           authService.loginUser("u", "p")
       }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_CREDENTIALS, exception.message)
    }

    @Test
    fun shouldFailLoginOnWrongPassword(){
        every { authRepository.findByUsernameIgnoreCase(any()) } returns UserEntity(username = "u", password = "")
        every { passwordService.checkPassword("p", any()) } returns false

        val exception = assertThrows<ApiException> {
            authService.loginUser("u", "p")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_CREDENTIALS, exception.message)
    }
}