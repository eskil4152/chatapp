package com.blikeng.chatapp.messaging.rabbit

import com.blikeng.chatapp.dtos.messaging.RabbitMessageDTO
import com.blikeng.chatapp.entities.ChatEntity
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.ChatFlushService
import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import jakarta.annotation.PreDestroy
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.amqp.core.Message
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

// ==========================
// Consumes queued chat messages from RabbitMQ and persists them in batches.
// Valid messages are written to the database, removed from Redis pending storage,
// and acknowledged manually. Invalid messages are dropped and acknowledged.
// Failed batches are nacked for retry.
// ==========================
@Component
class ChatFlushConsumer(
    private val userRepository: UserRepository,
    private val chatFlushService: ChatFlushService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    private val log: Logger = getLogger(ChatFlushConsumer::class.java)
    private val lock = Any()
    private val pending = mutableListOf<PendingRabbitMessage>()

    companion object {
        private const val BATCH_SIZE = 50
    }

    data class PendingRabbitMessage(
        val payload: RabbitMessageDTO,
        val channel: Channel,
        val deliveryTag: Long
    )

    // ==========================
    // RabbitMQ intake
    // ==========================
    @RabbitListener(
        queues = ["chat.buffer"],
        containerFactory = "rabbitListenerContainerFactory"
    )
    fun onMessage(
        payload: RabbitMessageDTO,
        message: Message,
        channel: Channel
    ) {
        var shouldFlush = false

        synchronized(lock) {
            pending.add(
                PendingRabbitMessage(
                    payload = payload,
                    channel = channel,
                    deliveryTag = message.messageProperties.deliveryTag
                )
            )

            if (pending.size >= BATCH_SIZE) {
                shouldFlush = true
            }
        }

        if (shouldFlush) {
            flushPending()
        }
    }

    // ==========================
    // Batch flushing
    // =========================
    @Scheduled(fixedDelayString = "\${chat.flush.fixedDelayMs:10000}")
    fun flushOnTimeout() {
        flushPending()
    }

    @PreDestroy
    fun shutdown() {
        flushPending()
    }

    private fun flushPending() {
        val batch = synchronized(lock) {
            if (pending.isEmpty()) return
            pending.toList().also { pending.clear() }
        }

        try {
            val userIds = batch.map { it.payload.userId }.toSet()
            val users = userRepository.findAllById(userIds).associateBy { it.id }

            val invalidMessages = mutableListOf<PendingRabbitMessage>()
            val validMessages = mutableListOf<PendingRabbitMessage>()

            batch.forEach { pendingMessage ->
                val msg = pendingMessage.payload
                if (users.containsKey(msg.userId)) {
                    validMessages.add(pendingMessage)
                } else {
                    invalidMessages.add(pendingMessage)
                }
            }

            invalidMessages.forEach { invalid ->
                log.error(
                    "Dropping Rabbit message {} because user {} was not found",
                    invalid.payload.id,
                    invalid.payload.userId
                )
                invalid.channel.basicAck(invalid.deliveryTag, false)
            }

            if (validMessages.isEmpty()) {
                return
            }

            val entities = validMessages.map { pendingMessage ->
                val msg = pendingMessage.payload
                val user = users.getValue(msg.userId)

                ChatEntity(
                    id = msg.id,
                    user = user,
                    roomId = msg.roomId,
                    message = msg.message,
                    ciphertext = msg.ciphertext,
                    nonce = msg.nonce,
                    keyVersion = msg.keyVersion,
                    timestamp = msg.timestamp
                )
            }

            chatFlushService.saveBatch(entities)

            validMessages.forEach { pendingMessage ->
                val msg = pendingMessage.payload
                val key = "chat.peek.${msg.roomId}"
                val json = objectMapper.writeValueAsString(msg)
                redisTemplate.opsForList().remove(key, 1, json)
            }

            validMessages.forEach { pendingMessage ->
                pendingMessage.channel.basicAck(pendingMessage.deliveryTag, false)
            }
        } catch (ex: Exception) {
            log.error("Failed to persist batch of size {}", batch.size, ex)

            batch.forEach { pendingMessage ->
                try {
                    pendingMessage.channel.basicNack(
                        pendingMessage.deliveryTag,
                        false,
                        true
                    )
                } catch (nackEx: Exception) {
                    log.error(
                        "Failed to nack RabbitMQ message with deliveryTag={}",
                        pendingMessage.deliveryTag,
                        nackEx
                    )
                }
            }
        }
    }
}