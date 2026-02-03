package com.blikeng.chatapp

import io.github.cdimascio.dotenv.dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatappApplication

fun main(args: Array<String>) {
	// Get secret from an environment variable (available in GitHub or production)
	System.getenv("JWT_SECRET")?.let {
		System.setProperty("JWT_SECRET", it)
	}

	// Will write secret from .env. Only works locally,
	// Does not overwrite GitHub or production, as no .env file will (should) be present
	val dotenv = dotenv()
	dotenv.entries().forEach { entry ->
		System.setProperty(entry.key, entry.value)
	}

	runApplication<ChatappApplication>(*args)
}
