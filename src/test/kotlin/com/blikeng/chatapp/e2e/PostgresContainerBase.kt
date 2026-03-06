package com.blikeng.chatapp.e2e

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

abstract class PostgresContainerBase {
    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("chatapp_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}