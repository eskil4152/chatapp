package com.blikeng.chatapp.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// ==========================
// Configures the shared Jackson ObjectMapper used across the application.
// JavaTimeModule is registered to support java.time types (e.g. Instant).
// WRITE_DATES_AS_TIMESTAMPS is disabled so dates serialize as ISO-8601 strings.
// ==========================
@Configuration
class JacksonConfig {
    @Bean
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
}