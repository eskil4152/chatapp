package com.blikeng.chatapp.security.auth

import com.blikeng.chatapp.services.UserService
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
// Also verifies the user exists in the database to reject deleted accounts
// before the connection is established.
// ==========================
@Component
class AuthHandshakeInterceptor(
    private val jwtService: JwtService,
    private val userService: UserService,
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

        userService.getUserById(id) ?: return false

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