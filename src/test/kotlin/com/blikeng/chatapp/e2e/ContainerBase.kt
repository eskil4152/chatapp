package com.blikeng.chatapp.e2e

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.RabbitMQContainer

abstract class ContainerBase {
    companion object {
        private val postgres =
            PostgreSQLContainer("postgres:16-alpine").apply {
                withDatabaseName("chatapp_test")
                withUsername("test")
                withPassword("test")
                start()
            }

        private val redis =
            GenericContainer("redis:7-alpine").apply {
                withExposedPorts(6379)
                start()
            }

        private val rabbit =
            RabbitMQContainer("rabbitmq:3-alpine").apply {
                start()
            }

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)

            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }

            registry.add("spring.rabbitmq.host", rabbit::getHost)
            registry.add("spring.rabbitmq.port") { rabbit.amqpPort }
            registry.add("spring.rabbitmq.username", rabbit::getAdminUsername)
            registry.add("spring.rabbitmq.password", rabbit::getAdminPassword)
        }
    }
}
