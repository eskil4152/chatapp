package com.blikeng.chatapp.securityTests.auth

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.security.auth.JwtService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNull
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

class JwtServiceTests {
    // ==========================
    // Tests for JwtService. Verifies:
    // - Generation of JWT tokens
    // - Validation of JWT tokens
    // - Extraction of username and userId from valid tokens
    // - Handling of invalid tokens
    // ==========================
    private val jwtService = JwtService("superSecretKeyForTheTestsWhichIsAbsolutelySecureEnoughAndFarEnoughBitsToBeAbleToBeMadeIntoASecureEnoughKey")

    @Test
    fun shouldGenerateToken(){
        val user = UserEntity(username = "u", password = "p")
        val token = jwtService.generateToken(user)

        assert(token.isNotBlank())
        assert(jwtService.validateToken(token) == JwtService.JwtPrincipal("u", user.id, "USER"))
    }

    @Test
    fun shouldValidateTokenWhenCorrectUser(){
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "p")
        val secondUser = UserEntity(id = UUID.randomUUID(), username = "u2", password = "p2")

        val token = jwtService.generateToken(user)
        val secondToken = jwtService.generateToken(secondUser)

        assert(jwtService.validateToken(secondToken)?.userId == secondUser.id)
        assert(jwtService.validateToken(token)?.userId != secondUser.id)

        assert(jwtService.validateToken(token)?.username == user.username)
        assert(jwtService.validateToken(token)?.username != secondUser.username)
    }

    @Test
    fun shouldReturnNullForInvalidToken() {
        val result = jwtService.validateToken("invalid.token")

        assertNull(result)
    }

    @Test
    fun shouldThrowWhenSecretIsTooShort() {
        val secret = "short-secret"

        val exception = assertFailsWith<IllegalArgumentException> {
            JwtService(secret)
        }

        assertTrue(exception.message!!.contains("Secret must be at least 64 bytes long"))
    }
}