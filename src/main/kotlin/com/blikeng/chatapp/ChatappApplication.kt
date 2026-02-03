package com.blikeng.chatapp

import com.blikeng.chatapp.security.configureSecret
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ChatappApplication

fun main(args: Array<String>) {
	configureSecret()
	runApplication<ChatappApplication>(*args)
}