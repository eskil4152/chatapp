package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.security.JwtService
import org.junit.jupiter.api.assertNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.Test

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
    fun shouldValidateTokenWhenCorrectUser(){
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
    fun shouldReturnNullForInvalidToken() {
        val result = jwtService.validateToken("invalid.token")

        assertNull(result)
    }
}