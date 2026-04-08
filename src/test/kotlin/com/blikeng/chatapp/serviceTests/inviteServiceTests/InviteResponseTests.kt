package com.blikeng.chatapp.serviceTests.inviteServiceTests

import com.blikeng.chatapp.dtos.invites.InviteResponse
import com.blikeng.chatapp.dtos.invites.InviteResponseDTO
import com.blikeng.chatapp.entities.InviteEntity
import com.blikeng.chatapp.entities.InviteStatus
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
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
class InviteResponseTests {
    // ==========================
    // Tests for InviteService respondToRequest. Verifies:
    // - Accepting and rejecting friend requests
    // - Accepting and rejecting room invites
    // - Accepting an open room invite
    // - Failure cases: invite not found, expired, wrong recipient
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

    private fun setAuth(userId: UUID) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    private fun pendingFriendRequest(from: UUID = user2.id, to: UUID = user1.id) = InviteEntity(
        type = InviteType.FRIEND_REQUEST,
        fromUserId = from,
        toUserId = to,
        expiresAt = Instant.now().plusSeconds(3600),
        status = InviteStatus.PENDING,
    )

    private fun pendingRoomInvite(to: UUID = user1.id) = InviteEntity(
        type = InviteType.ROOM_INVITE,
        fromUserId = user2.id,
        toUserId = to,
        roomId = roomId,
        expiresAt = Instant.now().plusSeconds(3600),
        status = InviteStatus.PENDING,
    )

    // ==========================
    // Friend request responses
    // ==========================
    @Test
    fun shouldAcceptFriendRequest() {
        setAuth(user1.id)
        val invite = pendingFriendRequest()
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { friendService.addFriend(user1.id, user2.id) } just Runs
        every { inviteRepository.save(any()) } answers { firstArg() }

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))

        verify(exactly = 1) { friendService.addFriend(user1.id, user2.id) }
        assertEquals(InviteStatus.ACCEPTED, invite.status)
    }

    @Test
    fun shouldRejectFriendRequest() {
        setAuth(user1.id)
        val invite = pendingFriendRequest()
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { inviteRepository.save(any()) } answers { firstArg() }

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.REJECTED))

        verify(exactly = 0) { friendService.addFriend(any(), any()) }
        assertEquals(InviteStatus.REJECTED, invite.status)
    }

    @Test
    fun shouldFailToRespondToFriendRequestAsWrongRecipient() {
        setAuth(user2.id)
        val wrongInvite = pendingFriendRequest(from = user1.id, to = UUID.randomUUID())
        every { userService.getUserById(user2.id) } returns user2
        every { inviteRepository.findById(wrongInvite.id) } returns Optional.of(wrongInvite)

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = wrongInvite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    // ==========================
    // Room invite responses
    // ==========================
    @Test
    fun shouldAcceptRoomInvite() {
        setAuth(user1.id)
        val invite = pendingRoomInvite()
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { roomService.joinRoom(user1.id, roomId) } just Runs
        every { inviteRepository.save(any()) } answers { firstArg() }

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))

        verify(exactly = 1) { roomService.joinRoom(user1.id, roomId) }
        assertEquals(InviteStatus.ACCEPTED, invite.status)
    }

    @Test
    fun shouldRejectRoomInvite() {
        setAuth(user1.id)
        val invite = pendingRoomInvite()
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { inviteRepository.save(any()) } answers { firstArg() }

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.REJECTED))

        verify(exactly = 0) { roomService.joinRoom(any(), any()) }
        assertEquals(InviteStatus.REJECTED, invite.status)
    }

    // ==========================
    // Open room invite
    // ==========================
    @Test
    fun shouldAcceptOpenRoomInvite() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns false
        every { inviteRepository.incrementUsagesIfAvailable(invite.id) } returns 1
        every { inviteRepository.deleteByToUserIdAndRoomId(user1.id, roomId) } just Runs
        every { roomService.joinRoom(user1.id, roomId) } just Runs
        val refreshed = InviteEntity(id = invite.id, type = invite.type, fromUserId = invite.fromUserId, roomId = invite.roomId, usages = 1, maxUsages = 10, expiresAt = invite.expiresAt, status = InviteStatus.PENDING)
        every { inviteRepository.findById(invite.id) } returns Optional.of(refreshed)
        every { inviteRepository.save(any()) } answers { firstArg() }

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))

        verify(exactly = 1) { roomService.joinRoom(user1.id, roomId) }
    }

    @Test
    fun shouldFailToRespondToRoomInviteAsWrongRecipient() {
        setAuth(user2.id)
        val wrongInvite = pendingRoomInvite(to = UUID.randomUUID())
        every { userService.getUserById(user2.id) } returns user2
        every { inviteRepository.findById(wrongInvite.id) } returns Optional.of(wrongInvite)

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = wrongInvite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    // ==========================
    // Open room invite failure cases
    // ==========================
    @Test
    fun shouldFailToAcceptOpenRoomInviteWhenAlreadyMember() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns true

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.CONFLICT, ex.status)
    }

    @Test
    fun shouldFailToAcceptOpenRoomInviteWhenBanned() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns true

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
    }

    @Test
    fun shouldFailToAcceptOpenRoomInviteWhenExhausted() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 10,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns false
        every { inviteRepository.incrementUsagesIfAvailable(invite.id) } returns 0

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToAcceptRoomInviteWhenRoomIdIsNull() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.ROOM_INVITE,
            fromUserId = user2.id,
            toUserId = user1.id,
            roomId = null,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToAcceptOpenRoomInviteWhenRoomIdIsNull() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = null,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailToAcceptOpenRoomInviteWhenRefreshedNotFound() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returnsMany listOf(Optional.of(invite), Optional.empty())
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns false
        every { inviteRepository.incrementUsagesIfAvailable(invite.id) } returns 1
        every { inviteRepository.deleteByToUserIdAndRoomId(user1.id, roomId) } just Runs
        every { roomService.joinRoom(user1.id, roomId) } just Runs

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldMarkInviteExhaustedWhenUsagesReachMax() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = 1,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns false
        every { inviteRepository.incrementUsagesIfAvailable(invite.id) } returns 1
        every { inviteRepository.deleteByToUserIdAndRoomId(user1.id, roomId) } just Runs
        every { roomService.joinRoom(user1.id, roomId) } just Runs
        val refreshed = InviteEntity(id = invite.id, type = invite.type, fromUserId = invite.fromUserId, roomId = invite.roomId, usages = 1, maxUsages = 1, expiresAt = invite.expiresAt, status = InviteStatus.PENDING)
        every { inviteRepository.findById(invite.id) } returns Optional.of(refreshed)
        every { inviteRepository.save(any()) } answers { firstArg() }

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))

        assertEquals(InviteStatus.EXHAUSTED, refreshed.status)
        verify(exactly = 1) { inviteRepository.save(refreshed) }
    }

    @Test
    fun shouldNotExhaustInviteWhenMaxUsagesIsNull() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = null,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns false
        every { inviteRepository.incrementUsagesIfAvailable(invite.id) } returns 1
        every { inviteRepository.deleteByToUserIdAndRoomId(user1.id, roomId) } just Runs
        every { roomService.joinRoom(user1.id, roomId) } just Runs
        val refreshed = InviteEntity(id = invite.id, type = invite.type, fromUserId = invite.fromUserId, roomId = invite.roomId, usages = 1, maxUsages = null, expiresAt = invite.expiresAt, status = InviteStatus.PENDING)
        every { inviteRepository.findById(invite.id) } returns Optional.of(refreshed)

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))

        verify(exactly = 0) { inviteRepository.save(any()) }
    }

    @Test
    fun shouldNotExhaustInviteWhenUsagesIsNull() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user2.id,
            roomId = roomId,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { userRoomRepository.existsByIdUserIdAndIdRoomId(user1.id, roomId) } returns false
        every { bannedUserService.isUserBanned(user1.id, roomId) } returns false
        every { inviteRepository.incrementUsagesIfAvailable(invite.id) } returns 1
        every { inviteRepository.deleteByToUserIdAndRoomId(user1.id, roomId) } just Runs
        every { roomService.joinRoom(user1.id, roomId) } just Runs
        val refreshed = InviteEntity(id = invite.id, type = invite.type, fromUserId = invite.fromUserId, roomId = invite.roomId, usages = null, maxUsages = 10, expiresAt = invite.expiresAt, status = InviteStatus.PENDING)
        every { inviteRepository.findById(invite.id) } returns Optional.of(refreshed)

        inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))

        verify(exactly = 0) { inviteRepository.save(any()) }
    }

    // ==========================
    // Common failure cases
    // ==========================
    @Test
    fun shouldFailWhenInviteNotPending() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.FRIEND_REQUEST,
            fromUserId = user2.id,
            toUserId = user1.id,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.ACCEPTED,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailWhenAcceptorNotFound() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = UUID.randomUUID().toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailWhenInviteNotFound() {
        setAuth(user1.id)
        val id = UUID.randomUUID()
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(id) } returns Optional.empty()

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailWhenInviteExpired() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.FRIEND_REQUEST,
            fromUserId = user2.id,
            toUserId = user1.id,
            expiresAt = Instant.now().minusSeconds(1),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findById(invite.id) } returns Optional.of(invite)
        every { inviteRepository.save(any()) } answers { firstArg() }

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = invite.id.toString(), response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
        assertEquals(InviteStatus.EXPIRED, invite.status)
    }

    @Test
    fun shouldFailWithInvalidInviteId() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1

        val ex = assertFailsWith<ApiException> {
            inviteService.respondToRequest(InviteResponseDTO(inviteId = "not-a-uuid", response = InviteResponse.ACCEPTED))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }
}
