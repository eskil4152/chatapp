package com.blikeng.chatapp.security.auth

import com.blikeng.chatapp.services.UserRevocationService
import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

// ==========================
// Validates the AUTH cookie during the WebSocket handshake and stores
// the authenticated user ID and username in the session attributes.
// Rejects deleted accounts via the Redis revocation set.
// ==========================
@Component
class AuthHandshakeInterceptor(
    private val jwtService: JwtService,
    private val userRevocationService: UserRevocationService,
) : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (request !is ServletServerHttpRequest) return false

        val cookies = request.servletRequest.cookies ?: return false
        val token = cookies.find { it.name == "AUTH" } ?: return false

        val (username, id) = jwtService.validateToken(token.value) ?: return false

        if (userRevocationService.isRevoked(id)) return false

        attributes["userId"] = id
        attributes["username"] = username

        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
        exception?.let { log.warn("Handshake failed: ${it.message}") }
    }
}