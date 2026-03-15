package com.blikeng.chatapp.securityTests.ratelimit

import com.blikeng.chatapp.security.ratelimit.RateLimitInterceptor
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// ==========================
// Tests for RateLimitInterceptor. Verifies:
// - Correct rate-limit key selection based on request path
// - Login, register, user edit, and password edit limits
// - Fallback rate limit for other /api paths
// - Handling of unknown IP addresses
// - Bypass of rate limiting for non-API endpoints
// ==========================
class RateLimitInterceptorTests {
    @Test
    fun shouldAllowLoginRequestWhenWithinLimit() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/api/login"
        every {
            rateLimitService.tryConsume("login:127.0.0.1", 5, any())
        } returns true

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)
    }

    @Test
    fun shouldRejectLoginRequestWhenOverLimit() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/api/login"
        every {
            rateLimitService.tryConsume("login:127.0.0.1", 5, any())
        } returns false

        val allowed = interceptor.preHandle(request, response, Any())

        assertFalse(allowed)
    }

    @Test
    fun shouldUseRegisterLimitForRegisterPath() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/api/register"
        every {
            rateLimitService.tryConsume("register:127.0.0.1", 10, any())
        } returns true

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)
    }

    @Test
    fun shouldUseUserLimitForUserPath() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/api/user/edit"
        every {
            rateLimitService.tryConsume("editUser:127.0.0.1", 2, any())
        } returns true

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)
    }

    @Test
    fun shouldUsePasswordLimitForPasswordPath() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/api/user/edit/password"
        every {
            rateLimitService.tryConsume("editPassword:127.0.0.1", 2, any())
        } returns true

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)
    }

    @Test
    fun shouldUseFallbackLimitForOtherPaths() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/api/rooms"
        every {
            rateLimitService.tryConsume("others:127.0.0.1", 60, any())
        } returns true

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)
    }

    @Test
    fun shouldProceedWithUnknownIfUnknownIP() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns null
        every { request.requestURI } returns "/api/rooms"
        every {
            rateLimitService.tryConsume("others:unknown", 60, any())
        } returns true

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)
    }

    @Test
    fun shouldAllowNonApiPathsWithoutRateLimit() {
        val rateLimitService = mockk<RateLimitService>()
        val interceptor = RateLimitInterceptor(rateLimitService)

        val request = mockk<HttpServletRequest>()
        val response = mockk<HttpServletResponse>(relaxed = true)

        every { request.remoteAddr } returns "127.0.0.1"
        every { request.requestURI } returns "/health"

        val allowed = interceptor.preHandle(request, response, Any())

        assertTrue(allowed)

        verify(exactly = 0) { rateLimitService.tryConsume(any(), any(), any()) }
    }
}