package com.blikeng.chatapp.serviceTests.inviteServiceTests

import com.blikeng.chatapp.dtos.invites.FriendRequestDTO
import com.blikeng.chatapp.dtos.invites.OpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.RoomInviteDTO
import com.blikeng.chatapp.entities.InviteEntity
import com.blikeng.chatapp.entities.InviteStatus
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.events.InviteSentEvent
import com.blikeng.chatapp.repositories.InviteRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.InviteService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.*
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

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

    private val user1 = UserEntity(id = UUID.randomUUID(), username = "user1", password = "pw")
    private val user2 = UserEntity(id = UUID.randomUUID(), username = "user2", password = "pw")
    private val roomId = UUID.randomUUID()
    private val expiresAt = System.currentTimeMillis() + 604800000L

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
    fun shouldFailToSendFriendRequestWithEmptyUsername() {
        setAuth(user1.id)

        val ex = assertFailsWith<ApiException> {
            inviteService.sendFriendRequest(FriendRequestDTO(username = "  "))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestWhenSenderInvalid() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestToNonExistentUser() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("ghost") } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.sendFriendRequest(FriendRequestDTO(username = "ghost"))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailToSendFriendRequestToYourself() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsernameIgnoreCase("user1") } returns user1

        val ex = assertFailsWith<ApiException> {
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

        val ex = assertFailsWith<ApiException> {
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendFriendRequest(FriendRequestDTO(username = "user2"))
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    // ==========================
    // sendRoomInvite
    // ==========================
    @Test
    fun shouldSendRoomInvite() {
        setAuth(user1.id)
        val userRoom = mockk<UserRoomEntity>()
        every { userRoom.role } returns RoomRole.OWNER
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns userRoom
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRepository.getUserByUsernameIgnoreCase(user2.username) } returns user2
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user2.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user2.id, roomId) } returns false
        every { inviteRepository.existsPendingRoomInvite(user2.id, roomId) } returns false
        every { inviteRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any<InviteSentEvent>()) } just Runs

        inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))

        verify(exactly = 1) { inviteRepository.save(any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<InviteSentEvent>()) }
    }

    @Test
    fun shouldFailToSendRoomInviteWhenSenderDeleted() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user1.username, roomId = roomId.toString(), expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWithInvalidRoomId() {
        setAuth(user1.id)

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = "not-a-uuid", expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToSendRoomInviteWhenNotMember() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.sendRoomInvite(RoomInviteDTO(type = "ROOM_INVITE", targetUsername = user2.username, roomId = roomId.toString(), expiresAt = expiresAt))
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
            InviteEntity(id = savedId, type = e.type, fromUserId = e.fromUserId, roomId = e.roomId, usages = e.usages, maxUsages = e.maxUsages, expiresAt = e.expiresAt, status = e.status)
        }

        val result = inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 10, expiresAt = expiresAt))

        assertEquals(savedId, result)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWithInvalidRoomId() {
        setAuth(user1.id)

        val ex = assertFailsWith<ApiException> {
            inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = "not-a-uuid", maxUsages = 5, expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWhenSenderDeleted() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5, expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWithZeroMaxUsages() {
        setAuth(user1.id)

        val ex = assertFailsWith<ApiException> {
            inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 0, expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWhenRoomNotFound() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.empty()

        val ex = assertFailsWith<ApiException> {
            inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5, expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToCreateOpenRoomInviteWhenNotMember() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { roomService.getRoom(roomId) } returns Optional.of(mockk<RoomEntity>())
        every { userRoomRepository.findByIdUserIdAndIdRoomId(user1.id, roomId) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5, expiresAt = expiresAt))
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

        val ex = assertFailsWith<ApiException> {
            inviteService.createOpenRoomInvite(OpenRoomInviteDTO(type = "OPEN_ROOM_INVITE", roomId = roomId.toString(), maxUsages = 5, expiresAt = expiresAt))
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }

    // ==========================
    // getPendingInvites
    // ==========================
    @Test
    fun shouldReturnPendingInvites() {
        val invite = InviteEntity(
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
    fun shouldReturnEmptyListWhenNoPendingInvites() {
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByToUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns emptyList()

        val result = inviteService.getPendingInvites(user1.id)

        assertEquals(0, result.size)
    }

    @Test
    fun shouldFailToGetPendingInvitesWhenUserNotFound() {
        every { userService.getUserById(user1.id) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.getPendingInvites(user1.id)
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }
}
