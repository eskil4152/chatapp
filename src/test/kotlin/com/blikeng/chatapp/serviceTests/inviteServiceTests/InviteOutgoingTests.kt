package com.blikeng.chatapp.serviceTests.inviteServiceTests

import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingFriendRequestDTO
import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingOpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.outgoing.OutgoingRoomInviteDTO
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
import kotlin.test.assertIs

@ExtendWith(MockKExtension::class)
class InviteOutgoingTests {
    // ==========================
    // Tests for InviteService.getOutgoingInvites.
    // Verifies:
    // - Returns OutgoingFriendRequestDTO for FRIEND_REQUEST invites
    // - Returns OutgoingRoomInviteDTO for ROOM_INVITE invites
    // - Returns OutgoingOpenRoomInviteDTO for OPEN_ROOM_INVITE invites
    // - Empty list when no outgoing invites
    // - Throws when user not found
    // - Throws InvalidInviteException for malformed invite data
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

    @Test
    fun shouldReturnOutgoingFriendRequests() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.FRIEND_REQUEST,
            fromUserId = user1.id,
            toUserId = user2.id,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { userRepository.findById(user2.id) } returns Optional.of(user2)

        val result = inviteService.getOutgoingInvites()

        assertEquals(1, result.size)
        assertIs<OutgoingFriendRequestDTO>(result[0])
        assertEquals(user2.id, (result[0] as OutgoingFriendRequestDTO).toUserId)
        assertEquals(user2.username, (result[0] as OutgoingFriendRequestDTO).toUsername)
    }

    @Test
    fun shouldReturnOutgoingRoomInvites() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.ROOM_INVITE,
            fromUserId = user1.id,
            toUserId = user2.id,
            roomId = roomId,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { userRepository.findById(user2.id) } returns Optional.of(user2)

        val result = inviteService.getOutgoingInvites()

        assertEquals(1, result.size)
        assertIs<OutgoingRoomInviteDTO>(result[0])
        assertEquals(roomId, (result[0] as OutgoingRoomInviteDTO).roomId)
        assertEquals(user2.username, (result[0] as OutgoingRoomInviteDTO).toUsername)
    }

    @Test
    fun shouldReturnOutgoingOpenRoomInvites() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user1.id,
            roomId = roomId,
            usages = 3,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val result = inviteService.getOutgoingInvites()

        assertEquals(1, result.size)
        assertIs<OutgoingOpenRoomInviteDTO>(result[0])
        assertEquals(roomId, (result[0] as OutgoingOpenRoomInviteDTO).roomId)
        assertEquals(3, (result[0] as OutgoingOpenRoomInviteDTO).usages)
        assertEquals(10, (result[0] as OutgoingOpenRoomInviteDTO).maxUsages)
    }

    @Test
    fun shouldReturnEmptyListWhenNoOutgoingInvites() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns emptyList()

        val result = inviteService.getOutgoingInvites()

        assertEquals(0, result.size)
    }

    @Test
    fun shouldFailToGetOutgoingInvitesWhenUserNotFound() {
        setAuth(user1.id)
        every { userService.getUserById(user1.id) } returns null

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailOnFriendRequestWithNullToUserId() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.FRIEND_REQUEST,
            fromUserId = user1.id,
            toUserId = null,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailOnRoomInviteWithNullToUserId() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.ROOM_INVITE,
            fromUserId = user1.id,
            toUserId = null,
            roomId = roomId,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailOnRoomInviteWithNullRoomId() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.ROOM_INVITE,
            fromUserId = user1.id,
            toUserId = user2.id,
            roomId = null,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { userRepository.findById(user2.id) } returns Optional.of(user2)

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailOnOpenRoomInviteWithNullRoomId() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user1.id,
            roomId = null,
            usages = 0,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailOnFriendRequestWhenTargetUserNotFound() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.FRIEND_REQUEST,
            fromUserId = user1.id,
            toUserId = user2.id,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { userRepository.findById(user2.id) } returns Optional.empty()

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailOnRoomInviteWhenTargetUserNotFound() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.ROOM_INVITE,
            fromUserId = user1.id,
            toUserId = user2.id,
            roomId = roomId,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)
        every { userRepository.findById(user2.id) } returns Optional.empty()

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.NOT_FOUND, ex.status)
    }

    @Test
    fun shouldFailOnOpenRoomInviteWithNullUsages() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user1.id,
            roomId = roomId,
            usages = null,
            maxUsages = 10,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun shouldFailOnOpenRoomInviteWithNullMaxUsages() {
        setAuth(user1.id)
        val invite = InviteEntity(
            type = InviteType.OPEN_ROOM_INVITE,
            fromUserId = user1.id,
            roomId = roomId,
            usages = 0,
            maxUsages = null,
            expiresAt = Instant.now().plusSeconds(3600),
            status = InviteStatus.PENDING,
        )
        every { userService.getUserById(user1.id) } returns user1
        every { inviteRepository.findByFromUserIdAndStatus(user1.id, InviteStatus.PENDING) } returns listOf(invite)

        val ex = assertFailsWith<ApiException> { inviteService.getOutgoingInvites() }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }
}
