package com.blikeng.chatapp.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// ==========================
// Configures the shared Jackson ObjectMapper used across the application.
// ==========================
@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }
}