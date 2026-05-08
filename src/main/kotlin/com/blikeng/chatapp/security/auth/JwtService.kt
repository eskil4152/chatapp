package com.blikeng.chatapp.security.auth

import com.blikeng.chatapp.entities.UserEntity
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

// ==========================
// Generates and validates JWT tokens used for cookie-based authentication.
// Tokens store the authenticated user's ID and username.
// ==========================
@Service
class JwtService(
    @Value("\${app.jwt.secret}")
    private val secret: String,
) {
    private val logger: Logger = getLogger(this::class.java)

    init {
        require(secret.toByteArray().size >= 64) { "Secret must be at least 64 bytes long" }
    }

    private fun key(): SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateToken(user: UserEntity): String =
        Jwts
            .builder()
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("role", user.role)
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .signWith(key())
            .compact()

    fun validateToken(token: String): JwtPrincipal? {
        return try {
            val claims =
                Jwts
                    .parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)

            val username: String = claims.body["username"].toString()
            val role = claims.body["role"].toString()
            val id = UUID.fromString(claims.body.subject)

            return JwtPrincipal(username, id, role)
        } catch (e: Exception) {
            logger.error("Invalid token: $e")
            null
        }
    }

    data class JwtPrincipal(
        val username: String,
        val userId: UUID,
        val role: String,
    )
}
