package com.blikeng.chatapp.serviceTests.roomServiceTests

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.room.AdministrationDTO
import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.dtos.room.UnbanDTO
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.notifications.events.RoomDeletedEvent
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
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
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional
import java.util.UUID

@ExtendWith(MockKExtension::class)
class RoomServiceManagementTests {
    // ==========================
    // Tests for RoomService room editing, deletion, and private messaging.
    // Verifies:
    // - Editing room name as owner
    // - Deleting room as owner
    // - Creating and retrieving private message rooms
    // - Deterministic room ID generation regardless of user order
    // - Auth, ownership, and membership failure cases
    // ==========================

    @MockK
    private lateinit var roomRepository: RoomRepository

    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var userRoomRepository: UserRoomRepository

    @MockK
    private lateinit var friendService: FriendService

    @MockK
    private lateinit var bannedUserService: BannedUserService

    @MockK
    private lateinit var eventPublisher: ApplicationEventPublisher

    @RelaxedMockK
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @RelaxedMockK
    private lateinit var objectMapper: ObjectMapper

    @InjectMockKs
    lateinit var roomService: RoomService

    @BeforeEach
    fun setupCache() {
        val ops = mockk<ValueOperations<String, String>>(relaxed = true)
        every { ops.get(any<String>()) } returns null
        every { redisTemplate.opsForValue() } returns ops
    }

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
    }

    // ==========================
    // Edit rooms
    // ==========================
    @Test
    fun shouldEditRoomName() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { roomRepository.findById(roomId) } returns
            Optional.of(
                RoomEntity(
                    id = roomId,
                    name = "r",
                    type = RoomType.GROUP,
                ),
            )
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.findUsersByRoomId(roomId) } returns emptyList()

        roomService.editRoom(
            RoomDTO(
                roomId = roomId.toString(),
                roomName = "newName",
                encrypted = false,
                role = RoomRole.OWNER,
                type = RoomType.GROUP,
            ),
        )

        verify { roomRepository.save(match { it.name == "newName" && it.id == roomId }) }
    }

    @Test
    fun shouldFailToEditRoomNameWithoutName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = UUID.randomUUID().toString(),
                        encrypted = false,
                        roomName = null,
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToEditRoomNameWithInvalidName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = UUID.randomUUID().toString(),
                        encrypted = false,
                        roomName = "",
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToEditRoomNameWithTooLongName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = UUID.randomUUID().toString(),
                        roomName = "a".repeat(101),
                        encrypted = false,
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_ROOM_NAME, exception.message)
    }

    @Test
    fun shouldFailToEditRoomWithoutBeingInRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns null

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = UUID.randomUUID().toString(),
                        encrypted = false,
                        roomName = "real name",
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToEditRoomWithoutBeingOwner() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = UUID.randomUUID().toString(),
                        encrypted = false,
                        roomName = "real name",
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToEditRoomIfNotFoundInDatabase() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { roomRepository.findById(roomId) } returns Optional.empty()

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = roomId.toString(),
                        encrypted = false,
                        roomName = "real name",
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToEditRoomWithInvalidId() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.editRoom(
                    RoomDTO(
                        roomId = "not a real UUID",
                        encrypted = false,
                        roomName = "new room name",
                        role = RoomRole.OWNER,
                        type = RoomType.GROUP,
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Delete rooms
    // ==========================
    @Test
    fun shouldDeleteRoom() {
        val userId = UUID.randomUUID()
        val room = RoomEntity(id = UUID.randomUUID(), name = "r", type = RoomType.GROUP)

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, room.id) } returns
            UserRoomEntity(id = UserRoomId(userId, room.id), role = RoomRole.OWNER, type = RoomType.GROUP)

        every { roomRepository.findById(room.id) } returns Optional.of(room)
        every { userRoomRepository.findUsersByRoomId(any()) } returns emptyList()
        every { roomRepository.delete(any()) } just Runs
        every { eventPublisher.publishEvent(any<RoomDeletedEvent>()) } just Runs

        roomService.deleteRoom(room.id.toString())

        verify(exactly = 1) {
            roomRepository.delete(room)
            eventPublisher.publishEvent(any<RoomDeletedEvent>())
        }
    }

    @Test
    fun shouldFailToDeleteRoomWithoutBeingInRoom() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns null

        val exception = assertThrows<ApiException> { roomService.deleteRoom(roomId.toString()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWithoutBeingOwner() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

        val exception = assertThrows<ApiException> { roomService.deleteRoom(roomId.toString()) }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWithInvalidId() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception = assertThrows<ApiException> { roomService.deleteRoom("not a real UUID") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Private message rooms
    // ==========================
    @Test
    fun shouldCreatePrivateMessageRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val friendId = UUID.randomUUID()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntityById(friendId, any()) } returns UserEntity(username = "us", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val roomId = roomService.getOrStartPrivateMessage(UserIdDTO(friendId.toString()))

        assertNotNull(roomId)
        verify(exactly = 1) { roomRepository.save(any()) }
        verify(exactly = 2) { userRoomRepository.save(any()) }
    }

    @Test
    fun shouldGetPrivateMessageRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val friendId = UUID.randomUUID()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntityById(friendId, any()) } returns UserEntity(username = "us", password = "")
        every { roomRepository.findById(any()) } returns
            Optional.of(RoomEntity(id = UUID.randomUUID(), name = "room", type = RoomType.GROUP))
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val roomId = roomService.getOrStartPrivateMessage(UserIdDTO(friendId.toString()))

        assertNotNull(roomId)
        verify(exactly = 0) { roomRepository.save(any()) }
        verify(exactly = 0) { userRoomRepository.save(any()) }
    }

    @Test
    fun shouldFailToGetPrivateMessagesWithInvalidFriendId() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertThrows<ApiException> { roomService.getOrStartPrivateMessage(UserIdDTO("not-a-uuid")) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetPrivateMessagesWhenFriendNotFound() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        val friendId = UUID.randomUUID()
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntityById(friendId, any()) } throws UserNotFoundException()

        val exception =
            assertThrows<ApiException> { roomService.getOrStartPrivateMessage(UserIdDTO(friendId.toString())) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetPrivateMessagesWithYourself() {
        val user = UserEntity(username = "u", password = "")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user.id, null, emptyList())

        every { userService.getUserById(user.id) } returns user
        every { friendService.getFriendEntityById(user.id, any()) } returns user
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception =
            assertThrows<ApiException> { roomService.getOrStartPrivateMessage(UserIdDTO(user.id.toString())) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Remove user from room
    // ==========================
    @Test
    fun shouldKickUserFromRoom() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId) } just Runs
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        roomService.removeUserFromRoom(
            AdministrationDTO(
                roomId = roomId.toString(),
                userId = targetId.toString(),
                action = RoomAction.KICK,
                reason = "",
            ),
        )

        verify(exactly = 1) { userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId) }
        verify(exactly = 0) { bannedUserService.banUser(any(), any()) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun shouldBanUserFromRoom() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId) } just Runs
        every { bannedUserService.banUser(targetId, roomId) } just Runs
        every { eventPublisher.publishEvent(any<Any>()) } just Runs

        roomService.removeUserFromRoom(
            AdministrationDTO(
                roomId = roomId.toString(),
                userId = targetId.toString(),
                action = RoomAction.BAN,
                reason = "",
            ),
        )

        verify(exactly = 1) { userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId) }
        verify(exactly = 1) { bannedUserService.banUser(targetId, roomId) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun shouldFailToRemoveUserWithTooLongReason() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.removeUserFromRoom(
                    AdministrationDTO(
                        roomId = UUID.randomUUID().toString(),
                        userId = UUID.randomUUID().toString(),
                        action = RoomAction.KICK,
                        reason = "a".repeat(501),
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_FIELD, exception.message)
    }

    @Test
    fun shouldFailToRemoveUserWhenNotInRoom() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, any()) } returns null

        val exception =
            assertThrows<ApiException> {
                roomService.removeUserFromRoom(
                    AdministrationDTO(
                        roomId = UUID.randomUUID().toString(),
                        userId = UUID.randomUUID().toString(),
                        action = RoomAction.KICK,
                        reason = "",
                    ),
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhenNotOwner() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

        val exception =
            assertThrows<ApiException> {
                roomService.removeUserFromRoom(
                    AdministrationDTO(
                        roomId = roomId.toString(),
                        userId = UUID.randomUUID().toString(),
                        action = RoomAction.KICK,
                        reason = "",
                    ),
                )
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWithInvalidRoomId() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.removeUserFromRoom(
                    AdministrationDTO(
                        roomId = "not-a-uuid",
                        userId = UUID.randomUUID().toString(),
                        action = RoomAction.KICK,
                        reason = "",
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWithInvalidTargetId() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.removeUserFromRoom(
                    AdministrationDTO(
                        roomId = UUID.randomUUID().toString(),
                        userId = "not-a-uuid",
                        action = RoomAction.KICK,
                        reason = "",
                    ),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhenTargetIsSelf() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)

        val exception =
            assertThrows<ApiException> {
                roomService.removeUserFromRoom(
                    AdministrationDTO(
                        roomId = roomId.toString(),
                        userId = userId.toString(),
                        action = RoomAction.KICK,
                        reason = "",
                    ),
                )
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWhenRoomNotFoundInRepository() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { roomRepository.findById(roomId) } returns Optional.empty()
        every { userRoomRepository.findUsersByRoomId(roomId) } returns emptyList()

        val exception = assertThrows<ApiException> { roomService.deleteRoom(roomId.toString()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    // ==========================
    // Get banned users
    // ==========================
    @Test
    fun shouldReturnBannedUsers() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()
        val bannedId = UUID.randomUUID()
        val bannedUser = UserEntity(id = bannedId, username = "banned", password = "")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { bannedUserService.getBannedUserIds(roomId) } returns listOf(bannedId)
        every { userService.getAllById(listOf(bannedId)) } returns listOf(bannedUser)

        val result = roomService.getAllBansForRoom(roomId.toString())

        assertEquals(1, result.size)
        assertEquals(bannedId, result[0].id)
        assertEquals("banned", result[0].username)
    }

    @Test
    fun shouldFailToGetBannedUsersWithInvalidRoomId() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertThrows<ApiException> { roomService.getAllBansForRoom("not-a-uuid") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetBannedUsersWhenRoomNotFound() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, any()) } returns null

        val exception = assertThrows<ApiException> { roomService.getAllBansForRoom(UUID.randomUUID().toString()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetBannedUsersWhenNotOwner() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

        val exception = assertThrows<ApiException> { roomService.getAllBansForRoom(roomId.toString()) }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    // ==========================
    // Unban user
    // ==========================
    @Test
    fun shouldUnbanUser() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.OWNER, type = RoomType.GROUP)
        every { bannedUserService.unbanUser(targetId, roomId) } just Runs

        roomService.unbanUser(UnbanDTO(roomId = roomId.toString(), userId = targetId.toString()))

        verify(exactly = 1) { bannedUserService.unbanUser(targetId, roomId) }
    }

    @Test
    fun shouldFailToUnbanWithInvalidUUID() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.unbanUser(UnbanDTO(roomId = "not-a-uuid", userId = UUID.randomUUID().toString()))
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToUnbanSelf() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.unbanUser(UnbanDTO(roomId = UUID.randomUUID().toString(), userId = userId.toString()))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToUnbanWhenRoomNotFound() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, any()) } returns null

        val exception =
            assertThrows<ApiException> {
                roomService.unbanUser(
                    UnbanDTO(
                        roomId = UUID.randomUUID().toString(),
                        userId = UUID.randomUUID().toString(),
                    ),
                )
            }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToUnbanWhenNotOwner() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns
            UserRoomEntity(id = UserRoomId(userId, roomId), role = RoomRole.MEMBER, type = RoomType.GROUP)

        val exception =
            assertThrows<ApiException> {
                roomService.unbanUser(UnbanDTO(roomId = roomId.toString(), userId = UUID.randomUUID().toString()))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldGenerateSamePrivateRoomForBothUserOrders() {
        val low = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val userLow = UserEntity(id = low, username = "low", password = "")
        val userHigh = UserEntity(id = high, username = "high", password = "")

        every { userService.getUserById(low) } returns userLow
        every { userService.getUserById(high) } returns userHigh
        every { friendService.getFriendEntityById(high, low) } returns userHigh
        every { friendService.getFriendEntityById(low, high) } returns userLow
        every { roomRepository.findById(any()) } returns Optional.empty()
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(low, null, emptyList())
        val roomId1 = roomService.getOrStartPrivateMessage(UserIdDTO(high.toString()))

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(high, null, emptyList())
        val roomId2 = roomService.getOrStartPrivateMessage(UserIdDTO(low.toString()))

        assertEquals(roomId1, roomId2)
    }
}
