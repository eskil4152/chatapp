package com.blikeng.chatapp.security.auth

import com.blikeng.chatapp.entities.UserEntity
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

// ==========================
// Generates and validates JWT tokens used for cookie-based authentication.
// Tokens store the authenticated user's ID and username.
// ==========================
@Service
class JwtService(
    @Value("\${app.jwt.secret}")
    private val secret: String
) {
    private val logger: Logger = getLogger(this::class.java)

    private fun key(): SecretKey =
        Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateToken(user: UserEntity): String {
        return Jwts.builder()
            .setSubject(user.id.toString())
            .claim("username", user.username)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .signWith(key(), SignatureAlgorithm.HS512)
            .compact()
    }

    fun validateToken(token: String): Pair<String, UUID>? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)

            val username: String = claims.body["username"].toString()
            val id = UUID.fromString(claims.body.subject)

            return Pair(username, id)
        } catch (e: Exception){
            logger.error("Invalid token: $e")
            null
        }
    }
}