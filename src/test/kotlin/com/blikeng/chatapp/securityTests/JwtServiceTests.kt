package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.security.JwtService
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTests {
    @Autowired
    lateinit var jwtService: JwtService

    @Test
    fun shouldGenerateToken(){
        val user = UserEntity(username = "u", password = "p")
        val token = jwtService.generateToken(user)

        assert(token.isNotBlank())
        assert(jwtService.validateToken(token) == Pair("u", user.id))
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
        val jwtService: JwtService = mockk()
        val user = UserEntity(username = "u", password = "p")

        assertFailsWith<RuntimeException> {
            jwtService.generateToken(user)
        }
    }
}