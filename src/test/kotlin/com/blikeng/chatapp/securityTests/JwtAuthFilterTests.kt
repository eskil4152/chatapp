package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.security.JwtAuthFilter
import com.blikeng.chatapp.security.JwtService
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtAuthFilterTests {
    private val jwtService = mockk<JwtService>()
    private val filter = JwtAuthFilter(jwtService)

    @BeforeEach
    fun setup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun shouldBeNullWithoutCookie() {
        val req = MockHttpServletRequest()
        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun shouldBeNullWithInvalidCookie() {
        every { jwtService.validateToken("bad") } returns null

        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "bad"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun shouldBeNullWithEmptyCookie() {
        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", ""))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun shouldSetUserId() {
        val userId = UUID.randomUUID()
        every { jwtService.validateToken("good") } returns ("u" to userId)

        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "good"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals(userId, auth.principal)
        assertEquals(0, auth.authorities.size)
    }

    @Test
    fun shouldNotOverrideExistingAuthentication() {
        val existing = UsernamePasswordAuthenticationToken("already", null, emptyList())
        SecurityContextHolder.getContext().authentication = existing

        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "good"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertEquals(existing, SecurityContextHolder.getContext().authentication)
        verify { jwtService wasNot Called }
    }

    @Test
    fun shouldBeNullWhenAuthCookieMissingButOtherCookiesExist() {
        val req = MockHttpServletRequest()
        req.setCookies(Cookie("OTHER", "x"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun shouldBeNullWithWhitespaceCookie() {
        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "   "))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify { jwtService wasNot Called }
    }
}