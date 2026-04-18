package com.blikeng.chatapp.serviceTests.chatServiceTests

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.ChatRepository
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import java.util.Collections.emptyList
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class ChatRoomSessionTests {
    // ==========================
    // Tests for ChatService session and room membership.
    // Verifies:
    // - Session registration and cleanup across multiple rooms
    // - Room join: auth, membership check, message sent to joiner
    // - Room leave: session removal, presence notification, empty-room cleanup
    // ==========================

    @MockK lateinit var chatRepository: ChatRepository
    @MockK lateinit var roomRepository: RoomRepository
    @MockK lateinit var userRoomRepository: UserRoomRepository
    @MockK lateinit var encrypt: ChatEncrypt
    @RelaxedMockK lateinit var redisTemplate: RedisTemplate<String, String>
    @MockK lateinit var rabbitTemplate: RabbitTemplate
    @MockK lateinit var listOps: ListOperations<String, String>
    @MockK lateinit var presenceHandler: PresenceHandler
    @MockK lateinit var userService: UserService
    @MockK(relaxed = true) lateinit var meterRegistry: MeterRegistry

    @InjectMockKs lateinit var chatService: ChatService
    private val objectMapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
        every { rabbitTemplate.convertAndSend(any<String>(), any<Any>()) } just Runs
        every { redisTemplate.convertAndSend(any<String>(), any<String>()) } returns 1L
        every { redisTemplate.opsForList() } returns listOps
        every { listOps.rightPush(any<String>(), any<String>()) } returns 1L
        every { listOps.range(any<String>(), any<Long>(), any<Long>()) } returns emptyList()
    }

    // ==========================
    // Session handling
    // ==========================
    @Test
    fun shouldRemoveSessionForEveryRoom() {
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))

        val userId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val roomId = UUID.randomUUID()
        val roomId2 = UUID.randomUUID()

        every { session.attributes } returns hashMapOf("userId" to userId)
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs
        every { userRoomRepository.findUserRoomsByRoomId(any()) } returns listOf()
        every { userService.getAllById(any()) } returns emptyList()

        chatService.joinRoom(roomId, session)
        chatService.joinRoom(roomId2, session)

        assertEquals(chatService.rooms[roomId]?.first(), session)
        assertEquals(chatService.rooms[roomId2]?.first(), session)

        chatService.removeSessionFromRooms(session)

        assertNull(chatService.rooms[roomId])
        assertNull(chatService.rooms[roomId2])
    }

    @Test
    fun shouldNotRemoveRoomIfOtherUsersArePresentInRoom() {
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs

        every { session2.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { session2.isOpen } returns true
        every { session2.sendMessage(any()) } just Runs
        every { userRoomRepository.findUserRoomsByRoomId(any()) } returns listOf(
            UserRoomEntity(UserRoomId(UUID.randomUUID(), roomId), RoomRole.MEMBER, RoomType.GROUP),
            UserRoomEntity(UserRoomId(UUID.randomUUID(), roomId), RoomRole.MEMBER, RoomType.GROUP)
        )
        every { userService.getAllById(any()) } returns emptyList()

        chatService.joinRoom(roomId, session)
        chatService.joinRoom(roomId, session2)

        chatService.removeSessionFromRooms(session)

        assertNotNull(chatService.rooms[roomId])
    }

    @Test
    fun shouldKeepPresenceWhenUserStillHasSessionInRoom() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        every { session1.attributes } returns hashMapOf("userId" to userId)
        every { session2.attributes } returns hashMapOf("userId" to userId)

        chatService.rooms[roomId] = mutableSetOf(session1, session2)

        chatService.removeSessionFromRooms(session1)

        assertNotNull(chatService.rooms[roomId])
        assertTrue(chatService.rooms[roomId]?.contains(session2) == true)
    }

    // ==========================
    // Room join
    // ==========================
    @Test
    fun shouldJoinRoom() {
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs
        every { userRoomRepository.findUserRoomsByRoomId(any()) } returns listOf()
        every { userService.getAllById(any()) } returns emptyList()

        chatService.joinRoom(roomId, session)

        assertEquals(session, chatService.rooms[roomId]?.first())
    }

    @Test
    fun shouldJoinRoomWithoutSendingMessagesWhenJoiningSessionIsClosed() {
        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { session.isOpen } returns false
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(roomId) } returns Optional.of(
            RoomEntity(id = roomId, name = "r", type = RoomType.GROUP)
        )

        chatService.joinRoom(roomId, session)

        assertTrue(chatService.rooms[roomId]?.contains(session) == true)
        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldSkipClosedExistingSessionsWhenSendingJoinPresence() {
        val roomId = UUID.randomUUID()

        val existingSession = mockk<WebSocketSession>(relaxed = true)
        val joiningSession = mockk<WebSocketSession>(relaxed = true)

        every { existingSession.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { joiningSession.attributes } returns hashMapOf("userId" to UUID.randomUUID())

        every { existingSession.isOpen } returns false
        every { joiningSession.isOpen } returns true

        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(roomId) } returns Optional.of(
            RoomEntity(id = roomId, name = "r", type = RoomType.GROUP)
        )
        every { joiningSession.sendMessage(any()) } just Runs
        every { userRoomRepository.findUserRoomsByRoomId(any()) } returns listOf()
        every { userService.getAllById(any()) } returns emptyList()

        chatService.joinRoom(roomId, existingSession)
        clearMocks(existingSession, answers = false, recordedCalls = true)
        chatService.joinRoom(roomId, joiningSession)

        verify(exactly = 0) { existingSession.sendMessage(any()) }
    }

    @Test
    fun shouldFailToJoinRoomIfNoUserIdInSessionAttributes() {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns emptyMap()

        val exception = assertFailsWith<ApiException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToJoinRoomIfNotAMember() {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } answers { null }

        val exception = assertFailsWith<ApiException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, exception.message)
    }

    @Test
    fun shouldFailToJoinRoomIfRoomDoesNotExistInDatabase() {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            chatService.joinRoom(UUID.randomUUID(), session)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
        assertEquals(ErrorMessages.ROOM_NOT_FOUND, exception.message)
    }


    // ==========================
    // Room leave
    // ==========================
    @Test
    fun shouldLeaveRoom() {
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val session = mockk<WebSocketSession>()

        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { session.isOpen } returns true
        every { session.sendMessage(any()) } just Runs
        every { userRoomRepository.findUserRoomsByRoomId(any()) } returns listOf()
        every { userService.getAllById(any()) } returns emptyList()

        chatService.joinRoom(roomId, session)
        assertEquals(session, chatService.rooms[roomId]?.first())

        chatService.leaveRoom(roomId, session)
        assertNull(chatService.rooms[roomId])
    }

    @Test
    fun shouldSkipClosedRemainingSessionsWhenLeavingRoom() {
        val roomId = UUID.randomUUID()

        val leavingSession = mockk<WebSocketSession>(relaxed = true)
        val remainingSession = mockk<WebSocketSession>(relaxed = true)

        every { leavingSession.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { remainingSession.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { remainingSession.isOpen } returns false
        every { presenceHandler.isUserOnline(any()) } returns false

        chatService.rooms[roomId] = CopyOnWriteArraySet(mutableSetOf(leavingSession, remainingSession))

        chatService.leaveRoom(roomId, leavingSession)

        verify(exactly = 0) { remainingSession.sendMessage(any()) }
    }

    @Test
    fun shouldDoNothingWhenLeavingNonExistingRoom() {
        every { presenceHandler.isUserOnline(any()) } returns false

        val session = mockk<WebSocketSession>()
        every { session.attributes } returns hashMapOf("userId" to UUID.randomUUID())

        assertNull(chatService.rooms[UUID.randomUUID()])
        chatService.leaveRoom(UUID.randomUUID(), session)
    }

    @Test
    fun shouldLeaveRoomEvenWhenUserIdIsNotPresentInSessionAttributes() {
        val session = mockk<WebSocketSession>()
        every { session.attributes } returns hashMapOf("userId" to "")

        chatService.leaveRoom(UUID.randomUUID(), session)
        assertNull(chatService.rooms[UUID.randomUUID()])
    }

    @Test
    fun shouldLeaveRoomOnlyFromOneSessionIfMultipleSessionsExist() {
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns UserRoomEntity(UserRoomId(UUID.randomUUID(), UUID.randomUUID()), RoomRole.MEMBER, RoomType.GROUP)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP))
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val session1 = mockk<WebSocketSession>()
        val session2 = mockk<WebSocketSession>()

        val attrs: MutableMap<String, Any> = hashMapOf("userId" to userId)
        every { session1.attributes } returns attrs
        every { session2.attributes } returns attrs
        every { session1.isOpen } returns true
        every { session2.isOpen } returns true
        every { session1.sendMessage(any()) } just Runs
        every { session2.sendMessage(any()) } just Runs
        every { userRoomRepository.findUserRoomsByRoomId(any()) } returns listOf()
        every { userService.getAllById(any()) } returns emptyList()

        chatService.joinRoom(roomId, session1)
        chatService.joinRoom(roomId, session2)

        assertEquals(2, chatService.rooms[roomId]?.size)

        chatService.leaveRoom(roomId, session1)

        assertEquals(1, chatService.rooms[roomId]?.size)
        assertFalse(chatService.rooms[roomId]?.contains(session1) == true)
        assertTrue(chatService.rooms[roomId]?.contains(session2) == true)
    }

    @Test
    fun shouldRemoveUserPresenceWhenRemainingRoomSessionHasInvalidUserIdType() {
        every { presenceHandler.isUserOnline(any()) } returns false

        val roomId = UUID.randomUUID()
        val leavingSession = mockk<WebSocketSession>()
        val invalidSession = mockk<WebSocketSession>()

        every { leavingSession.attributes } returns hashMapOf("userId" to UUID.randomUUID())
        every { invalidSession.attributes } returns hashMapOf("userId" to "not-a-uuid")
        every { leavingSession.sendMessage(any()) } just Runs
        every { invalidSession.isOpen } returns true
        every { invalidSession.sendMessage(any()) } just Runs

        chatService.rooms[roomId] = CopyOnWriteArraySet(mutableSetOf(leavingSession, invalidSession))

        chatService.leaveRoom(roomId, leavingSession)

        assertNotNull(chatService.rooms[roomId])
        assertEquals(1, chatService.rooms[roomId]?.size)
        assertTrue(chatService.rooms[roomId]?.contains(invalidSession) == true)
    }

    // ==========================
    // getUsersInRoom
    // ==========================
    @Test
    fun shouldIncludeRoleWhenGettingUsersInRoom() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val userRoom = UserRoomEntity(UserRoomId(userId, roomId), RoomRole.MODERATOR, RoomType.GROUP)
        val user = UserEntity(id = userId, username = "mod", password = "")

        every { userRoomRepository.findUserRoomsByRoomId(roomId) } returns listOf(userRoom)
        every { userService.getAllById(listOf(userId)) } returns listOf(user)
        every { presenceHandler.isUserOnline(userId) } returns true

        val result = chatService.getUsersInRoom(roomId)

        assertEquals(1, result.size)
        assertEquals(RoomRole.MODERATOR, result[0].role)
        assertEquals(userId, result[0].id)
        assertTrue(result[0].online)
    }

    @Test
    fun shouldReturnNullRoleWhenUserNotInRoomMap() {
        val roomId = UUID.randomUUID()
        val mappedUserId = UUID.randomUUID()
        val unmappedUserId = UUID.randomUUID()

        val userRoom = UserRoomEntity(UserRoomId(mappedUserId, roomId), RoomRole.MEMBER, RoomType.GROUP)
        val user = UserEntity(id = unmappedUserId, username = "ghost", password = "")

        every { userRoomRepository.findUserRoomsByRoomId(roomId) } returns listOf(userRoom)
        every { userService.getAllById(listOf(mappedUserId)) } returns listOf(user)
        every { presenceHandler.isUserOnline(unmappedUserId) } returns false

        val result = chatService.getUsersInRoom(roomId)

        assertEquals(1, result.size)
        assertNull(result[0].role)
    }

    // ==========================
    // Presence notifications
    // ==========================
    @Test
    fun shouldPublishRoomPresenceToRedis() {
        val roomId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userRoomRepository.findAllIdRoomIdsByIdUserId(userId) } returns listOf(roomId)

        chatService.notifyRoomPresence(userId, true)

        verify(exactly = 1) { redisTemplate.convertAndSend("room:$roomId", any<String>()) }
    }

    @Test
    fun shouldPublishPresenceForEachRoom() {
        val roomId1 = UUID.randomUUID()
        val roomId2 = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { userRoomRepository.findAllIdRoomIdsByIdUserId(userId) } returns listOf(roomId1, roomId2)

        chatService.notifyRoomPresence(userId, false)

        verify(exactly = 1) { redisTemplate.convertAndSend("room:$roomId1", any<String>()) }
        verify(exactly = 1) { redisTemplate.convertAndSend("room:$roomId2", any<String>()) }
    }
}