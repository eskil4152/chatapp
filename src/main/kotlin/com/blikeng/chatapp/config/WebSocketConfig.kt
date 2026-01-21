package com.blikeng.chatapp.config

import com.blikeng.chatapp.websocker.ChatWebSockerHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(private val chatHandler: ChatWebSockerHandler) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(chatHandler, "/ws/chat")
            .setAllowedOrigins("*")
    }

}