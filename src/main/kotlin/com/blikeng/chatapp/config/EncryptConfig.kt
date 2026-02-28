package com.blikeng.chatapp.config

import com.blikeng.chatapp.security.ChatEncrypt
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EncryptConfig {
    @Bean
    fun chatEncrypt(@Value("\${app.message-key-v1-b64}") keyV1B64: String): ChatEncrypt =
        ChatEncrypt(keyV1B64)
}