package com.blikeng.chatapp.config

import com.blikeng.chatapp.security.AuthHandshakeInterceptor
import com.blikeng.chatapp.websocket.ChatWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val chatHandler: ChatWebSocketHandler,
    private val authHandshakeInterceptor: AuthHandshakeInterceptor
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(chatHandler, "/ws")
            .addInterceptors(authHandshakeInterceptor)
            .setAllowedOriginPatterns("*")
    }
}