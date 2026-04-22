package com.blikeng.chatapp.securityTests.auth

import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.auth.JwtService
import com.blikeng.chatapp.services.UserRevocationService
import org.springframework.core.env.Environment
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

class JwtAuthFilterTests {
    // ==========================
    // Tests for JwtAuthFilter. Verifies:
    // - Extraction of the JWT token from request cookies
    // - Validation of the token through JwtService
    // - Population of the SecurityContext on valid authentication
    // - Existing authentication is not overridden
    // - Failure cases: missing AUTH cookie, wrong cookie name, invalid token, empty cookie, and whitespace-only cookie
    // ==========================
    private val jwtService = mockk<JwtService>()
    private val userRevocationService = mockk<UserRevocationService>()
    private val environment = mockk<Environment>()
    private val filter = JwtAuthFilter(jwtService, userRevocationService, environment)

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
    fun shouldSetAuthenticationWhenTokenIsValid() {
        val userId = UUID.randomUUID()
        every { jwtService.validateToken("good") } returns JwtService.JwtPrincipal(
            username = "u",
            userId = userId,
            role = "ADMIN"
        )
        every { userRevocationService.isRevoked(userId) } returns false
        every { userRevocationService.isBanned(userId) } returns false

        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "good"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        val authTemp = SecurityContextHolder.getContext().authentication
        val auth = requireNotNull(authTemp)

        assertNotNull(auth)
        assertEquals(userId, auth.principal)
        assertEquals("u", auth.credentials)
        assertEquals(1, auth.authorities.size)
        assertTrue(auth.authorities.any { it.authority == "ROLE_ADMIN" })
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

    @Test
    fun shouldNotAuthenticateRevokedUser() {
        val userId = UUID.randomUUID()
        every { jwtService.validateToken("good") } returns JwtService.JwtPrincipal(
            username = "u",
            userId = userId,
            role = "USER"
        )
        every { userRevocationService.isRevoked(userId) } returns true

        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "good"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun shouldNotAuthenticateBannedUser() {
        val userId = UUID.randomUUID()
        every { jwtService.validateToken("good") } returns JwtService.JwtPrincipal(
            username = "u",
            userId = userId,
            role = "USER"
        )
        every { userRevocationService.isRevoked(userId) } returns false
        every { userRevocationService.isBanned(userId) } returns true
        every { environment.activeProfiles } returns arrayOf("test")

        val req = MockHttpServletRequest()
        req.setCookies(Cookie("AUTH", "good"))

        val res = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(req, res, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}