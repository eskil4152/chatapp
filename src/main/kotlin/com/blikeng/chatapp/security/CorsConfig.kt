package com.blikeng.chatapp.security

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig: WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "https://eskil4152.github.io",
                "https://chatapp.blikeng.com"
                )
            .allowedMethods("GET", "POST", "PUT", "OPTIONS", "PATCH", "DELETE")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}