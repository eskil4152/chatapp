package com.blikeng.chatapp.security.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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

            if (principal != null) {
                val authorities = listOf(
                    SimpleGrantedAuthority("ROLE_${principal.role}")
                )

                val auth = UsernamePasswordAuthenticationToken(
                    principal.userId,
                    principal.username,
                    authorities
                )

                SecurityContextHolder.getContext().authentication = auth
            }
        }

        chain.doFilter(request, response)
    }
}