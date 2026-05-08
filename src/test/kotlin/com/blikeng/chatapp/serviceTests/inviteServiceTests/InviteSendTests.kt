package com.blikeng.chatapp.serviceTests.inviteServiceTests

import com.blikeng.chatapp.dtos.invites.FriendRequestDTO
import com.blikeng.chatapp.dtos.invites.OpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.RoomInviteDTO
import com.blikeng.chatapp.entities.InviteEntity
import com.blikeng.chatapp.entities.InviteStatus
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.notifications.events.InviteSentEvent
import com.blikeng.chatapp.repositories.InviteRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.InviteService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class InviteSendTests {
    // ==========================
    // Tests for InviteService send operations. Verifies:
    // - Sending a friend request: success, validation failures, duplicate/conflict checks
    // - Sending a room invite: success, permission checks, target validation
    // - Creating an open room invite: success, validation, permission checks
    // - Fetching pending invites for the current user
    // ==========================

    @InjectMockKs private lateinit var inviteService: InviteService

    @MockK private lateinit var userService: UserService

    @MockK private lateinit var userRepository: UserRepository

    @MockK private lateinit var userRoomRepository: UserRoomRepository

    @MockK private lateinit var friendService: FriendService

    @MockK private lateinit var inviteRepository: InviteRepository

    @MockK private lateinit var roomService: RoomService

    @MockK private lateinit var bannedUserService: BannedUserService

    @RelaxedMockK private lateinit var eventPublisher: ApplicationEventPublisher

    @RelaxedMockK private lateinit var redisTemplate: RedisTemplate<String, String>

    @RelaxedMockK private lateinit var objectMapper: ObjectMapper

    private val user1 = UserEntity(id = UUID.randomUUID(), username = "user1", password = "pw")
    private val user2 = UserEntity(id = UUID.randomUUID(), username = "user2", password = "pw")
    private val roomId = UUID.randomUUID()

    @BeforeEach
    fun setupCache() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
    }

    private fun setAuth(userId: UUID) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    // ==========================
    // sendFriendRequest
    // ==========================
    @Test
    fun shouldSendFriendRequest() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns false
        every { friendService.areFriends(user1.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any<InviteSentEvent>()) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        verify(exactly = 1) { inviteRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<InviteSentEvent>()) }
    }

    @Test
    fun shouldSendFriendRequestWithNullRoomNameInEvent() {
        setAuth(user1.id)
        val slot = slot<InviteSentEvent>()
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns false
        every { friendService.areFriends(user1.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(capture(slot)) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        assertEquals(null, slot.captured.invite.roomName)
    }

    @Test
    fun shouldSendFriendRequestWithRoomNameInEventWhenSavedEntityHasRoomId() {
        setAuth(user1.id)
        val room = RoomEntity(name = "Test Room", type = RoomType.GROUP)
        val slot = slot<InviteSentEvent>()
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns false
        every { friendService.areFriends(user1.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = e.id,
                type = e.type,
                fromUserId = e.fromUserId,
                toUserId = e.toUserId,
                roomId = roomId,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }
        every { roomService.getRoom(roomId) } returns Optional.of(room)
        every { eventPublisher.publishEvent(capture(slot)) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        assertEquals("Test Room", slot.captured.invite.roomName)
    }

    @Test
    fun shouldSendFriendRequestWithNullRoomNameInEventWhenSavedEntityHasRoomIdButRoomGone() {
        setAuth(user1.id)
        val slot = slot<InviteSentEvent>()
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns false
        every { friendService.areFriends(user1.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = e.id,
                type = e.type,
                fromUserId = e.fromUserId,
                toUserId = e.toUserId,
                roomId = roomId,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }
        every { roomService.getRoom(roomId) } returns Optional.empty()
        every { eventPublisher.publishEvent(capture(slot)) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        assertEquals(null, slot.captured.invite.roomName)
    }

    @Test
    fun shouldFailToSendFriendRequestWithEmptyUsername() {
        setAuth(user1.id)

        val ex =
            assertThrows<ApiException> {
                inviteService.sendFriendRequest(FriendRequestDTO(username = "  "))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestWhenSenderInvalid() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex =
            assertThrows<ApiException> {
                inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestToNonExistentUser() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("ghost") } returns null

        val ex =
            assertThrows<ApiException> {
                inviteService.sendFriendRequest(FriendRequestDTO(username = "ghost"))
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestToYourself() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user1") } returns user1

        val ex =
            assertThrows<ApiException> {
                inviteService.sendFriendRequest(FriendRequestDTO(username = "user1"))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestWhenAlreadyPending() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns true

        val ex =
            assertThrows<ApiException> {
                inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))
            }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestWhenAlreadyFriends() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns false
        every { friendService.areFriends(user1.id, user2.id) } returns true

        val ex =
            assertThrows<ApiException> {
                inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))
            }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    // ==========================
    // sendRoomInvite
    // ==========================
    @Test
    fun shouldSendRoomInvite() {
        val room =
            RoomEntity(
                name = "Test Room",
                type = RoomType.GROUP,
            )

        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(room)
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any<InviteSentEvent>()) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        verify(exactly = 1) { inviteRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<InviteSentEvent>()) }
    }

    @Test
    fun shouldSendRoomInviteWithNullRoomNameInEventWhenRoomDisappears() {
        val room = RoomEntity(name = "Test Room", type = RoomType.GROUP)

        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returnsMany listOf(Optional.of(room), Optional.empty())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }
        val slot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(slot)) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        assertEquals(null, slot.captured.invite.roomName)
    }

    @Test
    fun shouldSendRoomInviteWithNullRoomNameInEventWhenSavedEntityHasNullRoomId() {
        val room = RoomEntity(name = "Test Room", type = RoomType.GROUP)

        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(room)
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = e.id,
                type = e.type,
                fromUserId = e.fromUserId,
                toUserId = e.toUserId,
                roomId = null,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }
        val slot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(slot)) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        assertEquals(null, slot.captured.invite.roomName)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenSenderDeleted() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteToYourself() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRepository.getUserByUsernameIgnoreCase(user1.username) } returns user1

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user1.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWithInvalidRoomId() {
        setAuth(user1.id)

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = "not-a-uuid"))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenNotMember() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns null

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWithoutPermission() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.MEMBER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteToAlreadyBannedUser() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns true

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenRoomNotFound() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.empty()

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenTargetUserNotFound() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns null

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenTargetAlreadyMember() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns true

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenAlreadyPending() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns true

        val ex =
            assertThrows<ApiException> {
                inviteService.sendRoomInvite(
                    RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()),
                )
            }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    // ==========================
    // createOpenRoomInvite
    // ==========================
    @Test
    fun shouldCreateOpenRoomInviteAndReturnId() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        val savedId = UUID.randomUUID()
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = savedId,
                type = e.type,
                fromUserId = e.fromUserId,
                roomId = e.roomId,
                usages = e.usages,
                maxUsages = e.maxUsages,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }

        val result =
            inviteService.createOpenRoomInvite(
                OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 10),
            )

        assertEquals(savedId, result)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWithInvalidRoomId() {
        setAuth(user1.id)

        val ex =
            assertThrows<ApiException> {
                inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = "not-a-uuid", maxUsages = 5))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWithZeroMaxUsages() {
        setAuth(user1.id)

        val ex =
            assertThrows<ApiException> {
                inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 0))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWhenRoomNotFound() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.empty()

        val ex =
            assertThrows<ApiException> {
                inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5))
            }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWhenNotMember() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns null

        val ex =
            assertThrows<ApiException> {
                inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5))
            }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWithoutPermission() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.MEMBER
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom

        val ex =
            assertThrows<ApiException> {
                inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5))
            }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }

    // ==========================
    // createOpenRoomInvite expiry branch
    // ==========================
    @Test
    fun shouldCreateOpenRoomInviteWithCustomExpiry() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        val customExpiry = System.currentTimeMillis() + 86400000L
        val savedId = UUID.randomUUID()
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = savedId,
                type = e.type,
                fromUserId = e.fromUserId,
                roomId = e.roomId,
                usages = e.usages,
                maxUsages = e.maxUsages,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }

        val result =
            inviteService.createOpenRoomInvite(
                OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5, expiresAt = customExpiry),
            )

        assertEquals(savedId, result)
    }

    // ==========================
    // getPendingInvites
    // ==========================
    @Test
    fun shouldReturnPendingInvites() {
        val invite =
            InviteEntity(
                type = InviteType.FRIEND_REQUEST,
                fromUserId = user2.id,
                toUserId = user1.id,
                expiresAt = Instant.now().plusSeconds(3600),
                status = InviteStatus.PENDING,
            )
        every { userService.getUserById(user1.id) } returns user1
        every { userService.getUserById(user2.id) } returns user2
        every { inviteRepository.findByToUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(1, result.size)
        assertEquals(invite.id, result[0].id)
        assertEquals(InviteType.FRIEND_REQUEST, result[0].type)
    }

    @Test
    fun shouldReturnPendingInvitesWithRoomName() {
        val room = RoomEntity(name = "Cool Room", type = RoomType.GROUP)
        val invite =
            InviteEntity(
                type = InviteType.ROOM_INVITE,
                fromUserId = user2.id,
                toUserId = user1.id,
                roomId = roomId,
                expiresAt = Instant.now().plusSeconds(3600),
                status = InviteStatus.PENDING,
            )
        every { userService.getUserById(user1.id) } returns user1
        every { userService.getUserById(user2.id) } returns user2
        every { inviteRepository.findByToUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { roomService.getRoom(roomId) } returns Optional.of(room)

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(1, result.size)
        assertEquals("Cool Room", result[0].roomName)
    }

    @Test
    fun shouldReturnPendingInvitesWithUnknownSenderWhenSenderDeleted() {
        val invite =
            InviteEntity(
                type = InviteType.FRIEND_REQUEST,
                fromUserId = user2.id,
                toUserId = user1.id,
                expiresAt = Instant.now().plusSeconds(3600),
                status = InviteStatus.PENDING,
            )
        every { userService.getUserById(user1.id) } returns user1
        every { userService.getUserById(user2.id) } returns null
        every { inviteRepository.findByToUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(1, result.size)
        assertEquals("Unknown", result[0].fromUsername)
    }

    @Test
    fun shouldReturnEmptyListWhenNoPendingInvites() {
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByToUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns emptyList()

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(0, result.size)
    }

    @Test
    fun shouldReturnPendingInvitesWithNullRoomNameWhenRoomNotFound() {
        val invite =
            InviteEntity(
                type = InviteType.ROOM_INVITE,
                fromUserId = user2.id,
                toUserId = user1.id,
                roomId = roomId,
                expiresAt = Instant.now().plusSeconds(3600),
                status = InviteStatus.PENDING,
            )
        every { userService.getUserById(user1.id) } returns user1
        every { userService.getUserById(user2.id) } returns user2
        every { inviteRepository.findByToUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { roomService.getRoom(roomId) } returns Optional.empty()

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(1, result.size)
        assertEquals(null, result[0].roomName)
    }

    // ==========================
    // sendFriendRequest event content
    // ==========================
    @Test
    fun shouldSendFriendRequestAndPublishEventWithCorrectContent() {
        val sender = UserEntity(id = user1.id, username = "user1", password = "pw", avatarUrl = "avatar-url")
        setAuth(sender.id)
        every { userService.getUserById(sender.id) } returns sender
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(sender.id, user2.id) } returns false
        every { friendService.areFriends(sender.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        val event = eventSlot.captured
        assertEquals(user2.id, event.toUserId)
        assertEquals(sender.username, event.invite.fromUsername)
        assertEquals("avatar-url", event.invite.fromAvatarUrl)
        assertEquals(null, event.invite.roomName)
        assertEquals(null, event.invite.roomId)
    }

    @Test
    fun shouldSendFriendRequestAndPublishEventWithNullAvatarUrl() {
        // sender has no avatar — fromAvatarUrl must be null in the event
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1 // avatarUrl defaults to null
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(user1.id, user2.id) } returns false
        every { friendService.areFriends(user1.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        assertEquals(null, eventSlot.captured.invite.fromAvatarUrl)
    }

    // ==========================
    // sendRoomInvite event content
    // ==========================
    @Test
    fun shouldSendRoomInviteAndPublishEventWithRoomName() {
        val room = RoomEntity(name = "My Room", type = RoomType.GROUP)
        val sender = UserEntity(id = user1.id, username = "user1", password = "pw", avatarUrl = "avatar-url")
        setAuth(sender.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(sender.id) } returns sender
        every { userRoomRepository.findByIdUserIdAndIdRoomId(sender.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returnsMany listOf(Optional.of(room), Optional.of(room))
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        val event = eventSlot.captured
        assertEquals(user2.id, event.toUserId)
        assertEquals(sender.username, event.invite.fromUsername)
        assertEquals("avatar-url", event.invite.fromAvatarUrl)
        assertEquals("My Room", event.invite.roomName)
        assertEquals(roomId, event.invite.roomId)
    }

    @Test
    fun shouldSendRoomInviteAndPublishEventWithNullRoomNameWhenRoomDisappears() {
        val room = RoomEntity(name = "My Room", type = RoomType.GROUP)
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returnsMany listOf(Optional.of(room), Optional.empty())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        assertEquals(null, eventSlot.captured.invite.roomName)
    }

    @Test
    fun shouldSendRoomInviteAndPublishEventWithNullRoomNameAndNonNullAvatarUrl() {
        val room = RoomEntity(name = "My Room", type = RoomType.GROUP)
        val sender = UserEntity(id = user1.id, username = "user1", password = "pw", avatarUrl = "avatar-url")
        setAuth(sender.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(sender.id) } returns sender
        every { userRoomRepository.findByIdUserIdAndIdRoomId(sender.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returnsMany listOf(Optional.of(room), Optional.empty())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        assertEquals("avatar-url", eventSlot.captured.invite.fromAvatarUrl)
        assertEquals(null, eventSlot.captured.invite.roomName)
    }

    @Test
    fun shouldSendRoomInviteAndPublishEventWithNullAvatarUrl() {
        val room = RoomEntity(name = "My Room", type = RoomType.GROUP)
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1 // avatarUrl defaults to null
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returnsMany listOf(Optional.of(room), Optional.of(room))
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString()))

        assertEquals(null, eventSlot.captured.invite.fromAvatarUrl)
        assertEquals("My Room", eventSlot.captured.invite.roomName)
    }

    @Test
    fun shouldSendRoomInviteAndPublishEventWithExactSavedRoomId() {
        val room = RoomEntity(name = "Saved Room", type = RoomType.GROUP)
        val sender =
            UserEntity(
                id = user1.id,
                username = "user1",
                password = "pw",
                avatarUrl = "avatar-url",
            )

        setAuth(sender.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(sender.id) } returns sender
        every { userRoomRepository.findByIdUserIdAndIdRoomId(sender.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returnsMany listOf(Optional.of(room), Optional.of(room))
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = e.id,
                type = e.type,
                fromUserId = e.fromUserId,
                toUserId = e.toUserId,
                roomId = roomId,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendRoomInvite(
            RoomInviteDTO(
                type = "ROOM_INVITE",
                targetUsername = user2.username,
                roomId = roomId.toString(),
            ),
        )

        assertEquals(roomId, eventSlot.captured.invite.roomId)
        assertEquals("Saved Room", eventSlot.captured.invite.roomName)
    }

    @Test
    fun shouldSendFriendRequestAndPublishEventWithExactNullRoomFields() {
        val sender =
            UserEntity(
                id = user1.id,
                username = "user1",
                password = "pw",
                avatarUrl = "avatar-url",
            )

        setAuth(sender.id)
        every { userService.getUserById(sender.id) } returns sender
        every { userRepository.getUserByUsernameIgnoreCase("user2") } returns user2
        every { inviteRepository.existsPendingFriendRequest(sender.id, user2.id) } returns false
        every { friendService.areFriends(sender.id, user2.id) } returns false
        every { inviteRepository.save(any()) } answers {
            val e = firstArg<InviteEntity>()
            InviteEntity(
                id = e.id,
                type = e.type,
                fromUserId = e.fromUserId,
                toUserId = e.toUserId,
                roomId = null,
                expiresAt = e.expiresAt,
                status = e.status,
            )
        }

        val eventSlot = slot<InviteSentEvent>()
        every { eventPublisher.publishEvent(capture(eventSlot)) } just Runs

        inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))

        assertEquals(null, eventSlot.captured.invite.roomId)
        assertEquals(null, eventSlot.captured.invite.roomName)
    }

    @Test
    fun shouldReturnCachedPendingInvites() {
        val cached =
            listOf(
                com.blikeng.chatapp.dtos.invites.PendingInviteDTO(
                    id = UUID.randomUUID(),
                    type = InviteType.FRIEND_REQUEST,
                    fromUserId = user2.id,
                    fromUsername = "user2",
                    fromAvatarUrl = null,
                    roomId = null,
                    roomName = null,
                    expiresAt = java.time.Instant.now(),
                ),
            )
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get("user:${user1.id}:pending_invites") } returns "cached-json"
        every { redisTemplate.opsForValue() } returns ops
        every {
            objectMapper.readValue(
                "cached-json",
                any<com.fasterxml.jackson.core.type.TypeReference<List<com.blikeng.chatapp.dtos.invites.PendingInviteDTO>>>(),
            )
        } returns cached

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(cached, result)
        verify(exactly = 0) { inviteRepository.findByToUserIdAndStatus(any(), any()) }
    }
}
