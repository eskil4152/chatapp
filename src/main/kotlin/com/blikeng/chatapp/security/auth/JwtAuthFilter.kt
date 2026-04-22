package com.blikeng.chatapp.security.auth

import com.blikeng.chatapp.services.UserRevocationService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// ==========================
// Extracts the AUTH cookie from incoming requests, validates the JWT,
// and populates the Spring Security context when authentication is valid.
// ==========================
@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userRevocationService: UserRevocationService,
    private val environment: Environment,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val token = request.cookies
            ?.firstOrNull { it.name == "AUTH" }
            ?.value

        if (!token.isNullOrBlank() && SecurityContextHolder.getContext().authentication == null) {
            val principal = jwtService.validateToken(token)

            if (principal != null && !userRevocationService.isRevoked(principal.userId)) {
                if (userRevocationService.isBanned(principal.userId)) {
                    val isProd = environment.activeProfiles.contains("prod")
                    val cookie = ResponseCookie.from("AUTH", "")
                        .httpOnly(true)
                        .secure(isProd)
                        .path("/")
                        .sameSite("Strict")
                        .maxAge(0)
                        .build()
                    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
                } else {
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_${principal.role}"))
                    val auth = UsernamePasswordAuthenticationToken(
                        principal.userId,
                        principal.username,
                        authorities
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }

        chain.doFilter(request, response)
    }
}