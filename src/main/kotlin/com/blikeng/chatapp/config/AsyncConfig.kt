package com.blikeng.chatapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor
import java.util.concurrent.Executors

// ==========================
// Enables async execution and provides a virtual-thread executor for sending
// WebSocket snapshots (pending invites, friend presence) on SYNC requests.
// Keeps snapshot delivery off the WS handler thread so the handler returns
// immediately — the client socket stays open while the executor delivers.
// Virtual threads are ideal here: snapshot work is pure I/O (Redis reads),
// so each task gets its own virtual thread with no pool size limit or queue.
// ==========================
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(name = ["snapshotExecutor"])
    fun snapshotExecutor(): Executor {
        return Executors.newVirtualThreadPerTaskExecutor()
    }

    @Bean(name = ["broadcastExecutor"])
    fun broadcastExecutor(): Executor {
        return Executors.newVirtualThreadPerTaskExecutor()
    }
}
