package com.blikeng.chatapp.messaging.redis

import jakarta.annotation.PostConstruct
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component
import java.util.*

// ==========================
// Subscribes to Redis room Pub/Sub channels and forwards incoming
// messages to the local WebSocket broadcaster for this instance.
// ==========================
@Component
class RedisMessageSubscriber (
    private val localBroadcaster: LocalBroadcaster,
    private val container: RedisMessageListenerContainer
) : MessageListener {

    @PostConstruct
    fun register() {
        container.addMessageListener(this, PatternTopic("room:*"))
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val payload = message.body.toString(Charsets.UTF_8)
        val channel = message.channel.toString(Charsets.UTF_8)
        val roomId = UUID.fromString(channel.removePrefix("room:"))

        localBroadcaster.broadcastRaw(roomId, payload)
    }
}