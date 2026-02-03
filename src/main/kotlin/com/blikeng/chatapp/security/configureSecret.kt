package com.blikeng.chatapp.security

import io.github.cdimascio.dotenv.dotenv

fun configureSecret() {
    System.getenv("JWT_SECRET")?.let {
        System.setProperty("JWT_SECRET", it)
    }

    val dotenv = dotenv { ignoreIfMissing = true }
    dotenv.entries().forEach { entry ->
        if (System.getProperty(entry.key) == null) System.setProperty(entry.key, entry.value)
    }
}