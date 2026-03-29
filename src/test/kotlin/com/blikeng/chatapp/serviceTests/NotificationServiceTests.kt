package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.events.UserRemovedEvent
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import com.blikeng.chatapp.services.NotificationService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import java.util.*

@ExtendWith(MockKExtension::class)
class NotificationServiceTests {
    // ==========================
    // Tests for NotificationService. Verifies:
    // - KICK action sends a notification to the kicked user's Redis channel
    // - BAN action sends a notification to the banned user's Redis channel
    // - Room deletion notifies all members via their individual channels
    // - Room deletion with no members sends no notifications
    // ==========================

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var objectMapper: ObjectMapper

    @InjectMockKs lateinit var notificationService: NotificationService

    @Test
    fun shouldSendKickedNotificationOnUserRemoved() {
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val event = UserRemovedEvent(targetId, roomId, RoomAction.KICK, "spamming")

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"ROOM_ACTION"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L

        notificationService.onUserRemoved(event)

        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(targetId), any<String>()) }
    }

    @Test
    fun shouldSendBannedNotificationOnUserRemoved() {
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val event = UserRemovedEvent(targetId, roomId, RoomAction.BAN, null)

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"ROOM_ACTION"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L

        notificationService.onUserRemoved(event)

        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(targetId), any<String>()) }
    }

    @Test
    fun shouldNotifyAllMembersOnRoomDeleted() {
        val roomId = UUID.randomUUID()
        val member1 = UUID.randomUUID()
        val member2 = UUID.randomUUID()
        val event = RoomDeletedEvent(roomId, "general", listOf(member1, member2))

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"ROOM_DELETED"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L

        notificationService.onRoomDeleted(event)

        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(member1), any<String>()) }
        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(member2), any<String>()) }
    }

    @Test
    fun shouldNotSendAnyNotificationWhenNoMembersOnRoomDeleted() {
        val roomId = UUID.randomUUID()
        val event = RoomDeletedEvent(roomId, "empty", emptyList())

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"ROOM_DELETED"}"""

        notificationService.onRoomDeleted(event)

        verify(exactly = 0) { redisTemplate.convertAndSend(any(), any<String>()) }
    }
}
