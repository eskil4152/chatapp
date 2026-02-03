package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.security.JwtService
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class JwtServiceTests {
    @BeforeEach
    fun setUp(){
        System.setProperty("JWT_SECRET", Keys.secretKeyFor(SignatureAlgorithm.HS512).encoded.toString(Charsets.UTF_8))
    }

    private val jwtService = JwtService()

    @Test
    fun shouldGenerateToken(){
        val user = UserEntity(username = "u", password = "p")
        val token = jwtService.generateToken(user)

        assert(token.isNotBlank())
        assert(jwtService.validateToken(token) == user.id)
    }

    @Test
    fun shouldNotValidateTokenWhenInvalidUser(){
        val user = UserEntity(id = UUID.randomUUID(), username = "u", password = "p")
        val secondUser = UserEntity(id = UUID.randomUUID(), username = "u2", password = "p2")

        val token = jwtService.generateToken(user)
        val secondToken = jwtService.generateToken(secondUser)

        assert(jwtService.validateToken(secondToken)?.second == secondUser.id)
        assert(jwtService.validateToken(token)?.second != secondUser.id)

        assert(jwtService.validateToken(token)?.first == user.username)
        assert(jwtService.validateToken(token)?.first != secondUser.username)
    }

    @Test
    fun shouldFailWhenSecretNotProvided(){
        System.clearProperty("JWT_SECRET")
        val user = UserEntity(username = "u", password = "p")

        assertFailsWith<RuntimeException> {
            jwtService.generateToken(user)
        }

    }
}