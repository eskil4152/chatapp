package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.invites.PendingInviteDTO
import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.events.InviteAcceptedEvent
import com.blikeng.chatapp.events.InviteSentEvent
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.events.UserJoinedRoomEvent
import com.blikeng.chatapp.events.UserRemovedEvent
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.messaging.redis.PresenceKeys
import com.blikeng.chatapp.services.NotificationService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.redis.core.RedisTemplate
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class NotificationServiceTests {
    // ==========================
    // Tests for NotificationService. Verifies:
    // - Invite sent notifies recipient
    // - Invite accepted notifies sender; friend request acceptance also sends mutual presence
    // - User joining a room broadcasts JOIN and ROOM_PRESENCE to the room channel
    // - KICK/BAN sends a notification to the affected user's channel
    // - Room deletion notifies all members via their individual channels
    // - Room deletion with no members sends no notifications
    // ==========================

    @MockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var objectMapper: ObjectMapper
    @MockK lateinit var presenceHandler: PresenceHandler

    @InjectMockKs lateinit var notificationService: NotificationService

    @Test
    fun shouldSendInviteReceivedNotificationOnInviteSent() {
        val toUserId = UUID.randomUUID()
        val invite = PendingInviteDTO(
            id = UUID.randomUUID(),
            type = InviteType.FRIEND_REQUEST,
            fromUserId = UUID.randomUUID(),
            roomId = null,
            expiresAt = Instant.now(),
        )
        val event = InviteSentEvent(toUserId = toUserId, invite = invite)

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"INVITE_RECEIVED"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L

        notificationService.onInviteSent(event)

        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(toUserId), any<String>()) }
    }

    @Test
    fun shouldSendInviteAcceptedNotificationToSenderOnRoomInvite() {
        val fromUserId = UUID.randomUUID()
        val toUserId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val event = InviteAcceptedEvent(fromUserId = fromUserId, toUserId = toUserId, toUsername = "user2", toAvatarUrl = null, type = InviteType.ROOM_INVITE, roomId = roomId)

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"INVITE_ACCEPTED"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L

        notificationService.onInviteAccepted(event)

        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(fromUserId), any<String>()) }
        verify(exactly = 0) { redisTemplate.convertAndSend(PresenceKeys.userChannel(toUserId), any<String>()) }
    }

    @Test
    fun shouldSendMutualPresenceOnFriendRequestAccepted() {
        val fromUserId = UUID.randomUUID()
        val toUserId = UUID.randomUUID()
        val event = InviteAcceptedEvent(fromUserId = fromUserId, toUserId = toUserId, toUsername = "user2", toAvatarUrl = null, type = InviteType.FRIEND_REQUEST, roomId = null)

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"FRIEND_PRESENCE"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L
        every { presenceHandler.isUserOnline(fromUserId) } returns true
        every { presenceHandler.isUserOnline(toUserId) } returns false

        notificationService.onInviteAccepted(event)

        // INVITE_ACCEPTED + FRIEND_PRESENCE(toUserId) both go to fromUserId
        verify(exactly = 2) { redisTemplate.convertAndSend(PresenceKeys.userChannel(fromUserId), any<String>()) }
        // FRIEND_PRESENCE(fromUserId) goes to toUserId
        verify(exactly = 1) { redisTemplate.convertAndSend(PresenceKeys.userChannel(toUserId), any<String>()) }
    }

    @Test
    fun shouldBroadcastJoinAndPresenceToRoomOnUserJoined() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val event = UserJoinedRoomEvent(userId = userId, username = "alice", roomId = roomId)

        every { objectMapper.writeValueAsString(any()) } returns """{"type":"JOIN"}"""
        every { redisTemplate.convertAndSend(any(), any<String>()) } returns 1L
        every { presenceHandler.isUserOnline(userId) } returns true

        notificationService.onUserJoinedRoom(event)

        verify(exactly = 2) { redisTemplate.convertAndSend(PresenceKeys.roomChannel(roomId), any<String>()) }
    }

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
