package com.blikeng.chatapp.security.ratelimit

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Duration

// ==========================
// File for API rate limiting. Sets max limits per minute for login, register, user edit, and password edit.
// Fallback rate limit for other /api paths. Non-covered paths have no limit.
// ==========================
@Component
class RateLimitInterceptor(
    private val rateLimitService: RateLimitService,
    @Value("\${rate-limit.login.max-tokens:5}") private val loginMaxTokens: Long,
    @Value("\${rate-limit.register.max-tokens:10}") private val registerMaxTokens: Long,
    @Value("\${rate-limit.edit-user.max-tokens:3}") private val editUserMaxTokens: Long,
    @Value("\${rate-limit.edit-password.max-tokens:3}") private val editPasswordMaxTokens: Long,
    @Value("\${rate-limit.others.max-tokens:60}") private val othersMaxTokens: Long,
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val ip = request.remoteAddr ?: "unknown"
        val path = request.requestURI

        val allowed = when {
            path == "/api/login" -> rateLimitService.tryConsume(
                key = "login:$ip",
                maxTokens = loginMaxTokens,
                window = Duration.ofMinutes(1)
            )

            path == "/api/register" -> rateLimitService.tryConsume(
                key = "register:$ip",
                maxTokens = registerMaxTokens,
                window = Duration.ofMinutes(1)
            )

            path == "/api/user/edit" -> rateLimitService.tryConsume(
                key = "editUser:$ip",
                maxTokens = editUserMaxTokens,
                window = Duration.ofMinutes(1)
            )

            path == "/api/user/edit/password" -> rateLimitService.tryConsume(
                key = "editPassword:$ip",
                maxTokens = editPasswordMaxTokens,
                window = Duration.ofMinutes(1)
            )

            path.startsWith("/api/") -> rateLimitService.tryConsume(
                key = "others:$ip",
                maxTokens = othersMaxTokens,
                window = Duration.ofMinutes(1)
            )

            else -> true
        }

        if (!allowed) {
            response.status = 429
            response.contentType = "text/plain"
            response.writer.write("Too many requests")
            return false
        }

        return true
    }
}