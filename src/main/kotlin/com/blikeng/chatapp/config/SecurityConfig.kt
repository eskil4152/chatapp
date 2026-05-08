package com.blikeng.chatapp.config

import com.blikeng.chatapp.security.auth.JwtAuthFilter
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

// ==========================
// Configures application security, password encoding, JWT-based authentication,
// and access rules for public and protected endpoints.
// ==========================
@Configuration
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .authorizeHttpRequests { authRequest ->
                authRequest.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                authRequest.requestMatchers("/api/login").permitAll()
                authRequest.requestMatchers("/api/register").permitAll()
                authRequest.requestMatchers("/error").permitAll()

                authRequest.requestMatchers("/api/admin/site-info").hasRole("TRUSTED")
                authRequest.requestMatchers("/api/admin/advanced-site-info").hasRole("ADMIN")
                authRequest.requestMatchers("/api/admin/**").hasRole("MODERATOR")

                authRequest.requestMatchers("/actuator/**").permitAll()
                authRequest.anyRequest().hasRole("USER")
            }.httpBasic { it.disable() }
            .formLogin { it.disable() }
            .cors { }
            .csrf { it.disable() }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token")
                }
            }

        return http.build()
    }
}
