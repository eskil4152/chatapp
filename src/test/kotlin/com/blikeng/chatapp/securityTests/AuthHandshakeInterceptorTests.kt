import com.blikeng.chatapp.security.AuthHandshakeInterceptor
import com.blikeng.chatapp.security.JwtService
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.socket.WebSocketHandler
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthHandshakeInterceptorTest {

    private val jwtService = mockk<JwtService>()
    private val interceptor = AuthHandshakeInterceptor(jwtService)

    @Test
    fun `beforeHandshake returns true for valid token`() {
        val username = "u"
        val id = UUID.randomUUID()
        val token = "token"

        val request = mockk<HttpServletRequest>()
        val servletRequest = ServletServerHttpRequest(request)
        val serverRequest = servletRequest as ServletServerHttpRequest
        val response = mockk<ServerHttpResponse>()
        val handler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { request.cookies } returns arrayOf(Cookie("AUTH", token))
        every { jwtService.validateToken(token) } returns Pair(username, id)

        val result = interceptor.beforeHandshake(serverRequest, response, handler, attributes)

        assertTrue(result)
        assertEquals(attributes["userId"], id)
        assertEquals(attributes["username"], username)
    }

    @Test
    fun `beforeHandshake returns false if request is not servlet`() {
        val request = mockk<ServletServerHttpRequest>()
        val response = mockk<ServerHttpResponse>()
        val handler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        val result = interceptor.beforeHandshake(request, response, handler, attributes)
        assertFalse(result)
    }

    @Test
    fun `beforeHandshake returns false if token missing`() {
        val request = mockk<HttpServletRequest>()
        val servletRequest = ServletServerHttpRequest(request)
        val serverRequest = servletRequest as ServletServerHttpRequest
        val response = mockk<ServerHttpResponse>()
        val handler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { request.cookies } returns null

        val result = interceptor.beforeHandshake(serverRequest, response, handler, attributes)
        assertFalse(result)
    }

    @Test
    fun `beforeHandshake returns false if token invalid`() {
        val token = "token"
        val request = mockk<HttpServletRequest>()
        val servletRequest = ServletServerHttpRequest(request)
        val serverRequest = servletRequest as ServletServerHttpRequest
        val response = mockk<ServerHttpResponse>()
        val handler = mockk<WebSocketHandler>()
        val attributes = mutableMapOf<String, Any>()

        every { request.cookies } returns arrayOf(Cookie("AUTH", token))
        every { jwtService.validateToken(token) } returns null

        val result = interceptor.beforeHandshake(serverRequest, response, handler, attributes)
        assertFalse(result)
    }
}