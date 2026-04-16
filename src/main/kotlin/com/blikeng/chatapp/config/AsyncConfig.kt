package com.blikeng.chatapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

// ==========================
// Enables async execution and provides a dedicated thread pool for sending
// WebSocket snapshots (pending invites, friend presence) on SYNC requests.
// Isolates snapshot DB queries from request-handling threads.
// ==========================
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(name = ["snapshotExecutor"])
    fun snapshotExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 20
            maxPoolSize = 50
            queueCapacity = 500
            setThreadNamePrefix("snapshot-")
            initialize()
        }
    }
}