package com.blikeng.chatapp.security

import io.github.cdimascio.dotenv.dotenv
import org.springframework.context.annotation.Bean

@Bean
fun configureSecret() {
    System.getenv("JWT_SECRET")?.let {
        System.setProperty("JWT_SECRET", it)
    }

    val dotenv = dotenv { ignoreIfMissing = true }
    dotenv.entries().forEach { entry ->
        System.setProperty(entry.key, entry.value)
    }
}