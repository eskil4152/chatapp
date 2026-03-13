package com.blikeng.chatapp.messagingTests

import com.blikeng.chatapp.messaging.redis.LocalBroadcaster
import com.blikeng.chatapp.messaging.redis.RedisMessageSubscriber
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import java.util.*
import kotlin.test.assertEquals
import org.springframework.data.redis.connection.MessageListener

@ExtendWith(MockKExtension::class)
class RedisMessageSubscriberTests {
    // ==========================
    // Tests for RedisMessageSubscriber.
    // Verifies that the Redis listener is registered on the correct
    // room pattern topic and that incoming Redis messages are
    // forwarded to the LocalBroadcaster.
    // ==========================

    @MockK lateinit var localBroadcaster: LocalBroadcaster
    @MockK lateinit var container: RedisMessageListenerContainer

    @InjectMockKs lateinit var subscriber: RedisMessageSubscriber

    @Test
    fun shouldRegisterListenerOnRoomPattern() {
        every { container.addMessageListener(any<MessageListener>(), any<PatternTopic>()) } just Runs

        subscriber.register()

        val topicSlot = slot<PatternTopic>()
        verify(exactly = 1) { container.addMessageListener(subscriber, capture(topicSlot)) }
        assertEquals("room:*", topicSlot.captured.topic)
    }

    @Test
    fun shouldBroadcastMessageFromRedisChannel() {
        val roomId = UUID.randomUUID()
        val payload = """{"type":"MESSAGE","content":"hello"}"""

        val message = mockk<Message>()
        every { message.body } returns payload.toByteArray(Charsets.UTF_8)
        every { message.channel } returns "room:$roomId".toByteArray(Charsets.UTF_8)

        every { localBroadcaster.broadcastRaw(any(), any()) } just Runs

        subscriber.onMessage(message, null)

        verify(exactly = 1) { localBroadcaster.broadcastRaw(roomId, payload) }
    }
}