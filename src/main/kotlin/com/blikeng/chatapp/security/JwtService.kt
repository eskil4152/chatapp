package com.blikeng.chatapp.security

import com.blikeng.chatapp.entities.UserEntity
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.persistence.Tuple
import org.springframework.stereotype.Service
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService {

    private val k = "keykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykeykey"
    private val key: SecretKey = Keys.hmacShaKeyFor(k.encodeToByteArray())

    fun generateToken(user: UserEntity): String {
        return Jwts.builder()
            .setSubject(user.id.toString())
            .claim("username", user.username)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun validateToken(token: String): UUID? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
            UUID.fromString(claims.body.subject)
        } catch (e: Exception){
            print("Caught Exception: $e")
            null
        }
    }

    fun validateToken2(token: String): Pair<String, UUID?>? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)

            val username: String = claims.body["username"].toString()
            val id: UUID? = UUID.fromString(claims.body.subject)

            return Pair(username, id)
        } catch (e: Exception){
            print("Caught Exception: $e")
            null
        }
    }
}