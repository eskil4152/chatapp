package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
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
        every { passwordService.encodePassword("password") } returns "ENC"
        every { authRepository.save(any()) } answers { firstArg() }
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val cookie = authService.registerUser("username", "password")
        assert(cookie == "TOKEN")
    }

    @Test
    fun shouldNotRegisterUserWithExistingUsername() {
        every { authRepository.existsByUsername(any()) } returns true
        every { passwordService.encodePassword(any()) } returns "ENCO"

        val exception = assertFailsWith<ApiException> {
            authService.registerUser("u", "p")
        }

        assertEquals(HttpStatus.CONFLICT, exception.status)
        assertEquals(ErrorMessages.USERNAME_EXISTS, exception.message)
    }

    @Test
    fun shouldNotRegisterUserWithTooShortUsername() {
        every { authRepository.existsByUsername(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            authService.registerUser("u", "password")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.SHORT_USERNAME, exception.message)
    }

    @Test
    fun shouldNotRegisterUserWithTooShortPassword() {
        every { authRepository.existsByUsername(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            authService.registerUser("username", "p")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.SHORT_PASSWORD, exception.message)
    }

    @Test
    fun shouldFailLoginOnNonExistingUser(){
        every { authRepository.findByUsername(any()) } returns null

       val exception = assertFailsWith<ApiException> {
           authService.loginUser("u", "p")
       }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_CREDENTIALS, exception.message)
    }

    @Test
    fun shouldFailLoginOnWrongPassword(){
        every { authRepository.findByUsername(any()) } returns UserEntity(username = "u", password = "")
        every { passwordService.checkPassword("p", any()) } returns false

        val exception = assertFailsWith<ApiException> {
            authService.loginUser("u", "p")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_CREDENTIALS, exception.message)
    }

    @Test
    fun shouldLoginSuccessfully(){
        every { authRepository.findByUsername(any()) } returns UserEntity(username = "u", password = "")
        every { passwordService.checkPassword("p", any()) } returns true
        every { jwtService.generateToken(any()) } returns "TOKEN"

        val res = authService.loginUser("u", "p")
        assert(res == "TOKEN")
    }
}