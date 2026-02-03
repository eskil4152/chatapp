package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.security.AuthHandshakeInterceptor
import com.blikeng.chatapp.security.JwtService
import io.mockk.Called
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import java.util.*
import kotlin.test.*

@ExtendWith(MockKExtension::class)
class AuthHandshakeInterceptorTests {
    @MockK
    lateinit var jwtService: JwtService

    lateinit var interceptor: AuthHandshakeInterceptor

    @BeforeEach
    fun setUp() {
        interceptor = AuthHandshakeInterceptor(jwtService)
    }

    @Test
    fun shouldPerformHandshake() {
        val userId = UUID.randomUUID()

        every { jwtService.validateToken("token") } returns ("user" to userId)

        val servletRequest = mockk<HttpServletRequest> {
            every { getHeader(any()) } returns "token"
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
    }

    @Test
    fun shouldFailWithoutToken() {
        val servletRequest = mockk<HttpServletRequest> {
            every { getHeader(any()) } returns null
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
    fun shouldFailWithInvalidToken() {
        every { jwtService.validateToken("fake_token") } returns null

        val servletRequest = mockk<HttpServletRequest> {
            every { getHeader(any()) } returns "fake_token"
        }

        val request = ServletServerHttpRequest(servletRequest)

        val result = interceptor.beforeHandshake(
            request,
            mockk(relaxed = true),
            mockk(relaxed = true),
            mutableMapOf()
        )

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
}