package com.blikeng.chatapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer

@Configuration
class RedisConfig {
    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        return container
    }
}