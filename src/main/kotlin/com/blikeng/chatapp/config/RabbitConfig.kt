package com.blikeng.chatapp.config

import org.springframework.amqp.core.AcknowledgeMode
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory
import org.springframework.amqp.rabbit.connection.ConnectionFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// ==========================
// Configures RabbitMQ infrastructure for chat persistence.
// Registers the durable chat buffer queue, JSON message conversion,
// and a manually acknowledged single-consumer listener setup.
// ==========================
@Configuration
class RabbitConfig {
    @Bean
    fun chatBufferQueue(): Queue = QueueBuilder.durable("chat.buffer").build()

    @Bean
    fun rabbitTemplate(connectionFactory: ConnectionFactory): RabbitTemplate {
        val template = RabbitTemplate(connectionFactory)
        template.messageConverter = JacksonJsonMessageConverter()
        return template
    }

    @Bean
    fun rabbitListenerContainerFactory(connectionFactory: ConnectionFactory): SimpleRabbitListenerContainerFactory {
        val factory = SimpleRabbitListenerContainerFactory()
        factory.setConnectionFactory(connectionFactory)
        factory.setMessageConverter(JacksonJsonMessageConverter())

        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL)
        factory.setPrefetchCount(50)
        factory.setConcurrentConsumers(1)
        factory.setMaxConcurrentConsumers(1)

        return factory
    }
}
