package com.blikeng.chatapp

import io.github.cdimascio.dotenv.dotenv
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatappApplication

fun main(args: Array<String>) {
	System.getenv("JWT_SECRET")?.let {
		System.setProperty("JWT_SECRET", it)
	}

	val dotenv = dotenv()
	dotenv.entries().forEach { entry ->
		System.setProperty(entry.key, entry.value)
	}

	runApplication<ChatappApplication>(*args)
}
