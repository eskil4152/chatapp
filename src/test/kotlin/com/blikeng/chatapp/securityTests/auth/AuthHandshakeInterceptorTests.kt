package com.blikeng.chatapp.securityTests.auth

import com.blikeng.chatapp.security.auth.AuthHandshakeInterceptor
import com.blikeng.chatapp.security.auth.JwtService
import com.blikeng.chatapp.services.UserRevocationService
import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import java.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

@ExtendWith(MockKExtension::class)
class AuthHandshakeInterceptorTests {
    // ==========================
    // Tests for AuthHandshakeInterceptor.
    // Verifies that the interceptor:
    // - Extracts the JWT token from the request cookies
    // - Validates the token using the JwtService
    // - Adds the user ID and username to the WebSocket handshake attributes
    // - Fail condition: Gets wrong cookie, no cookie, invalid cookie
    // ==========================

    @MockK
    lateinit var jwtService: JwtService

    @MockK
    lateinit var userRevocationService: UserRevocationService

    lateinit var interceptor: AuthHandshakeInterceptor

    @BeforeEach
    fun setUp() {
        interceptor = AuthHandshakeInterceptor(jwtService, userRevocationService)
    }

    @Test
    fun shouldPerformHandshake() {
        val cookiesList: Array<Cookie> = arrayOf(Cookie("AUTH", "token"))

        val userId = UUID.randomUUID()

        every { jwtService.validateToken("token") } returns JwtService.JwtPrincipal(
            username = "user",
            userId = userId,
            role = "USER"
        )
        every { userRevocationService.isRevoked(userId) } returns false
        every { userRevocationService.isBanned(userId) } returns false

        val servletRequest = mockk<HttpServletRequest> {
            every { cookies } returns cookiesList
        }

        val request = ServletServerHttpRequest(servletRequest)
        val response = mockk<ServerHttpResponse>(relaxed = true)
        val wsHandler = mockk<WebSocketHandler>(relaxed = true)

        val attributes = mutableMapOf<String, Any>()

        val result = interceptor.beforeHandshake(
            request,
            response,
            wsHandler,
            attributes
        )

        assertTrue(result)
        assertEquals(userId, attributes["userId"])
        assertEquals("user", attributes["username"])

        verify(exactly = 1) { jwtService.validateToken("token") }
    }

    @Test
    fun shouldFailWithoutCookie() {
        val servletRequest = mockk<HttpServletRequest> {
            every { cookies } returns null
        }

        val request = ServletServerHttpRequest(servletRequest)

        val result = interceptor.beforeHandshake(
            request,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mutableMapOf()
        )

        assertFalse(result)

        verify { jwtService wasNot Called }
    }

    @Test
    fun shouldFailWithoutCorrectCookie() {
        val cookiesList: Array<Cookie> = arrayOf(Cookie("AUT", "token"))
        val servletRequest = mockk<HttpServletRequest> {
            every { cookies } returns cookiesList
        }

        val request = ServletServerHttpRequest(servletRequest)

        val result = interceptor.beforeHandshake(
            request,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mutableMapOf()
        )

        assertFalse(result)
        verify { jwtService wasNot Called }
    }

    @Test
    fun shouldFailWithInvalidCookie() {
        val cookiesList: Array<Cookie> = arrayOf(Cookie("AUTH", "fake_token"))

        every { jwtService.validateToken("fake_token") } returns null

        val servletRequest = mockk<HttpServletRequest> {
            every { cookies } returns cookiesList
        }

        val request = ServletServerHttpRequest(servletRequest)

        val result = interceptor.beforeHandshake(
            request,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mutableMapOf()
        )

        verify(exactly = 1) { jwtService.validateToken("fake_token") }
        assertFalse(result)
    }

    @Test
    fun failsWhenRequestIsNotServletBased() {
        val request: ServerHttpRequest = mockk()

        val result = interceptor.beforeHandshake(
            request,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mutableMapOf()
        )

        assertFalse(result)
        verify { jwtService wasNot Called }
    }

    @Test
    fun shouldGetExceptionFromAfterHandshake() {
        val exception = Exception("Something went wrong")
        interceptor.afterHandshake(mockk(), mockk(), mockk(), exception)
    }

    @Test
    fun afterHandshakeShouldPass(){
        interceptor.afterHandshake(mockk(), mockk(), mockk(), null)
    }

    @Test
    fun shouldFailWhenUserIsRevoked() {
        val cookiesList: Array<Cookie> = arrayOf(Cookie("AUTH", "token"))
        val userId = UUID.randomUUID()

        every { jwtService.validateToken("token") } returns JwtService.JwtPrincipal(
            username = "user",
            userId = userId,
            role = "USER"
        )
        every { userRevocationService.isRevoked(userId) } returns true

        val servletRequest = mockk<HttpServletRequest> {
            every { cookies } returns cookiesList
        }

        val request = ServletServerHttpRequest(servletRequest)
        val attributes = mutableMapOf<String, Any>()

        val result = interceptor.beforeHandshake(request, mockk(relaxed = true), mockk(relaxed = true), attributes)

        assertFalse(result)
        assertTrue(attributes.isEmpty())
    }

    @Test
    fun shouldFailWhenUserIsBanned() {
        val cookiesList: Array<Cookie> = arrayOf(Cookie("AUTH", "token"))
        val userId = UUID.randomUUID()

        every { jwtService.validateToken("token") } returns JwtService.JwtPrincipal(
            username = "user",
            userId = userId,
            role = "USER"
        )
        every { userRevocationService.isRevoked(userId) } returns false
        every { userRevocationService.isBanned(userId) } returns true

        val servletRequest = mockk<HttpServletRequest> {
            every { cookies } returns cookiesList
        }

        val request = ServletServerHttpRequest(servletRequest)
        val attributes = mutableMapOf<String, Any>()

        val result = interceptor.beforeHandshake(request, mockk(relaxed = true), mockk(relaxed = true), attributes)

        assertFalse(result)
        assertTrue(attributes.isEmpty())
    }
}