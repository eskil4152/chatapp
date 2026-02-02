package com.blikeng.chatapp.security

import com.blikeng.chatapp.entities.UserEntity
import io.github.cdimascio.dotenv.Dotenv
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

@Service
class JwtService {
    val secret = System.getProperty("JWT_SECRET") ?: "secret"
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.encodeToByteArray())

    fun generateToken(user: UserEntity): String {
        return Jwts.builder()
            .setSubject(user.id.toString())
            .claim("username", user.username)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun validateToken(token: String): Pair<String, UUID>? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)

            val username: String = claims.body["username"].toString()
            val id = UUID.fromString(claims.body.subject)

            return Pair(username, id)
        } catch (e: Exception){
            print("Caught Exception: $e")
            null
        }
    }
}