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
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals


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
        val jwtService: JwtService = mockk()
        val user = UserEntity(username = "u", password = "p")

        every { jwtService.getSystemVariable() } returns null
        every { jwtService.getDotenvVariable() } returns null

        assertFailsWith<RuntimeException> {
            jwtService.generateToken(user)
        }
    }

    @Test
    fun shouldUseDefaultDotenvFallbackWhenNoPropertiesSet() {
        System.clearProperty("DOTENV_DIR")
        System.clearProperty("DOTENV_FILE")
        System.clearProperty("DOTENV_KEY")

        val jwtService = spyk(JwtService())

        every { jwtService.getSystemVariable() } returns null
        every { jwtService.getDotenvVariable() } returns null

        assertFailsWith<RuntimeException> {
            jwtService.key()
        }
    }

    @Test
    fun shouldGetSecretFromDotenv(){
        val jwtService = spyk(JwtService())

        every { jwtService.getSystemVariable() } returns null

        System.setProperty("DOTENV_DIR", "src/test/resources/")
        System.setProperty("DOTENV_FILE", "test.env")
        System.setProperty("DOTENV_KEY", "JWT_SECRET_TEST")

        val key = jwtService.key()

        assertEquals(
            key.encoded.toString(Charsets.UTF_8),
            "superSecretKeyForDotenvWhichIsAbsolutelySecureEnoughAndFarEnoughBitsToBeAbleToBeMadeIntoASecureEnoughKey")
        assertNotEquals(
            key.encoded.toString(Charsets.UTF_8),
            "superSecretKeyForSysEnvWhichIsAbsolutelySecureEnoughAndFarEnoughBitsToBeAbleToBeMadeIntoASecureEnoughKey"
        )

        System.clearProperty("DOTENV_DIR")
        System.clearProperty("DOTENV_FILE")
        System.clearProperty("DOTENV_KEY")
    }

    @Test
    fun shouldGetSecretFromSystemProperty(){
        val jwtService = spyk(JwtService())

        every { jwtService.getSystemVariable() } returns "superSecretKeyForSysEnvWhichIsAbsolutelySecureEnoughAndFarEnoughBitsToBeAbleToBeMadeIntoASecureEnoughKey"
        every { jwtService.getDotenvVariable() } returns null

        val key = jwtService.key()

        assertEquals(
            key.encoded.toString(Charsets.UTF_8),
            "superSecretKeyForSysEnvWhichIsAbsolutelySecureEnoughAndFarEnoughBitsToBeAbleToBeMadeIntoASecureEnoughKey")
        assertNotEquals(
            key.encoded.toString(Charsets.UTF_8),
            "superSecretKeyForDotenvWhichIsAbsolutelySecureEnoughAndFarEnoughBitsToBeAbleToBeMadeIntoASecureEnoughKey"
        )
    }

    @Test
    fun shouldThrowIfNeitherEnvIsAvailable(){
        val jwtService = spyk(JwtService())

        every { jwtService.getSystemVariable() } returns null
        every { jwtService.getDotenvVariable() } returns null

        assertFailsWith<RuntimeException> { jwtService.key() }
    }
}