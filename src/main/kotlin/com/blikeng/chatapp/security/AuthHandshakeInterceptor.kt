package com.blikeng.chatapp.security

import org.slf4j.LoggerFactory
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

@Component
class AuthHandshakeInterceptor(private val jwtService: JwtService) : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        if (request !is ServletServerHttpRequest) return false

        val token = request.servletRequest.getHeader("AUTH") ?: return false

        val (username, id) = jwtService.validateToken(token) ?: return false

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