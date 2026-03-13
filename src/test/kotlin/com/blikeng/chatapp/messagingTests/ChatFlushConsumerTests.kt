package com.blikeng.chatapp.messagingTests

import com.blikeng.chatapp.dtos.RabbitMessageDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.messaging.rabbit.ChatFlushConsumer
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.ChatFlushService
import com.fasterxml.jackson.databind.ObjectMapper
import com.rabbitmq.client.Channel
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.core.Message
import org.springframework.amqp.core.MessageProperties
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import java.util.*

@ExtendWith(MockKExtension::class)
class ChatFlushConsumerTests {
    // ==========================
    // Tests for ChatFlushConsumer.
    // Verifies that the consumer:
    // - Flushes queued chat batches on timeout or when batch size is reached
    // - Persists valid messages and clears them from Redis
    // - Acknowledges invalid messages without persisting them
    // - Nacks messages when persistence fails
    // - Continues safely if nack handling itself fails
    // ==========================

    @MockK lateinit var userRepository: UserRepository
    @MockK lateinit var chatFlushService: ChatFlushService
    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var listOps: ListOperations<String, String>
    @MockK lateinit var objectMapper: ObjectMapper

    @InjectMockKs lateinit var consumer: ChatFlushConsumer

    @BeforeEach
    fun setup() {
        every { redisTemplate.opsForList() } returns listOps
        every { listOps.remove(any<String>(), any(), any<String>()) } returns 1L
        every { objectMapper.writeValueAsString(any()) } returns "{}"
    }

    @Test
    fun shouldFlushValidMessageOnTimeout() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val user = UserEntity(id = userId, username = "u", password = "")

        val payload = RabbitMessageDTO(
            roomId = roomId,
            userId = userId,
            username = "u",
            message = "hello"
        )

        val props = MessageProperties().apply { deliveryTag = 10L }
        val message = Message("{}".toByteArray(), props)
        val channel = mockk<Channel>()

        every { userRepository.findAllById(any<Set<UUID>>()) } returns listOf(user)
        every { chatFlushService.saveBatch(any()) } just Runs
        every { channel.basicAck(10L, false) } just Runs

        consumer.onMessage(payload, message, channel)
        consumer.flushOnTimeout()

        verify(exactly = 1) { chatFlushService.saveBatch(any()) }
        verify(exactly = 1) { channel.basicAck(10L, false) }
        verify(exactly = 1) { listOps.remove("chat.peek.$roomId", 1, "{}") }
    }

    @Test
    fun shouldFlushImmediatelyWhenBatchSizeReached() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val user = UserEntity(id = userId, username = "u", password = "")

        every { userRepository.findAllById(any<Set<UUID>>()) } returns listOf(user)
        every { chatFlushService.saveBatch(any()) } just Runs

        val channel = mockk<Channel>()
        every { channel.basicAck(any(), false) } just Runs

        repeat(50) { i ->
            val payload = RabbitMessageDTO(
                roomId = roomId,
                userId = userId,
                username = "u",
                message = "hello-$i"
            )

            val props = MessageProperties().apply { deliveryTag = i.toLong() + 1 }
            val message = Message("{}".toByteArray(), props)

            consumer.onMessage(payload, message, channel)
        }

        verify(exactly = 1) { chatFlushService.saveBatch(match { it.size == 50 }) }
        verify(exactly = 50) { channel.basicAck(any(), false) }
    }

    @Test
    fun shouldAckInvalidUserAndNotSaveBatch() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        val payload = RabbitMessageDTO(
            roomId = roomId,
            userId = userId,
            username = "u",
            message = "hello"
        )

        val props = MessageProperties().apply { deliveryTag = 10L }
        val message = Message("{}".toByteArray(), props)
        val channel = mockk<Channel>()

        every { userRepository.findAllById(any<Set<UUID>>()) } returns emptyList()
        every { channel.basicAck(10L, false) } just Runs

        consumer.onMessage(payload, message, channel)
        consumer.flushOnTimeout()

        verify(exactly = 0) { chatFlushService.saveBatch(any()) }
        verify(exactly = 1) { channel.basicAck(10L, false) }
    }

    @Test
    fun shouldNackBatchWhenSaveFails() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val user = UserEntity(id = userId, username = "u", password = "")

        val payload = RabbitMessageDTO(
            roomId = roomId,
            userId = userId,
            username = "u",
            message = "hello"
        )

        val props = MessageProperties().apply { deliveryTag = 10L }
        val message = Message("{}".toByteArray(), props)
        val channel = mockk<Channel>()

        every { userRepository.findAllById(any<Set<UUID>>()) } returns listOf(user)
        every { chatFlushService.saveBatch(any()) } throws RuntimeException("db fail")
        every { channel.basicNack(10L, false, true) } just Runs

        consumer.onMessage(payload, message, channel)
        consumer.flushOnTimeout()

        verify(exactly = 1) { channel.basicNack(10L, false, true) }
    }

    @Test
    fun shouldContinueWhenNackFails() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val user = UserEntity(id = userId, username = "u", password = "")

        val payload = RabbitMessageDTO(
            roomId = roomId,
            userId = userId,
            username = "u",
            message = "hello"
        )

        val props = MessageProperties().apply { deliveryTag = 10L }
        val message = Message("{}".toByteArray(), props)
        val channel = mockk<Channel>()

        every { userRepository.findAllById(any<Set<UUID>>()) } returns listOf(user)
        every { chatFlushService.saveBatch(any()) } throws RuntimeException("db fail")
        every { channel.basicNack(10L, false, true) } throws RuntimeException("nack fail")

        consumer.onMessage(payload, message, channel)

        assertDoesNotThrow {
            consumer.flushOnTimeout()
        }

        verify(exactly = 1) { channel.basicNack(10L, false, true) }
    }
}