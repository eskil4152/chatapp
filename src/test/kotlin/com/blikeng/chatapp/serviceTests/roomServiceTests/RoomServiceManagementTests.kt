package com.blikeng.chatapp.serviceTests.roomServiceTests

import com.blikeng.chatapp.dtos.room.AdministrationDTO
import com.blikeng.chatapp.dtos.room.RoomAction
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.entities.*
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.events.RoomDeletedEvent
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var userRoomRepository: UserRoomRepository
    @MockK private lateinit var friendService: FriendService
    @MockK private lateinit var bannedUserService: BannedUserService
    @MockK private lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMockKs lateinit var roomService: RoomService

    @AfterEach
    fun clearSecurity() { SecurityContextHolder.clearContext() }

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
        every { roomRepository.findById(roomId) } returns Optional.of(RoomEntity(id = roomId, name = "r", type = RoomType.GROUP))
        every { roomRepository.save(any()) } answers { firstArg() }

        roomService.editRoom(RoomDTO(roomId = roomId.toString(), roomName = "newName", encrypted = false, role = RoomRole.OWNER, type = RoomType.GROUP))

        verify { roomRepository.save(match { it.name == "newName" && it.id == roomId }) }
    }

    @Test
    fun shouldFailToEditRoomNameWithoutName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(roomId = UUID.randomUUID().toString(), encrypted = false, roomName = null, role = RoomRole.OWNER, type = RoomType.GROUP))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToEditRoomNameWithInvalidName() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(roomId = UUID.randomUUID().toString(), encrypted = false, roomName = "", role = RoomRole.OWNER, type = RoomType.GROUP))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToEditRoomWithoutBeingInRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(any(), any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(roomId = UUID.randomUUID().toString(), encrypted = false, roomName = "real name", role = RoomRole.OWNER, type = RoomType.GROUP))
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

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(roomId = UUID.randomUUID().toString(), encrypted = false, roomName = "real name", role = RoomRole.OWNER, type = RoomType.GROUP))
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

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(roomId = roomId.toString(), encrypted = false, roomName = "real name", role = RoomRole.OWNER, type = RoomType.GROUP))
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToEditRoomWithInvalidId() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(roomId = "not a real UUID", encrypted = false, roomName = "new room name", role = RoomRole.OWNER, type = RoomType.GROUP))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToEditRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.editRoom(RoomDTO(UUID.randomUUID().toString(), "new room name", false, RoomRole.OWNER, RoomType.GROUP))
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

        val exception = assertFailsWith<ApiException> { roomService.deleteRoom(roomId.toString()) }
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

        val exception = assertFailsWith<ApiException> { roomService.deleteRoom(roomId.toString()) }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWithInvalidId() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception = assertFailsWith<ApiException> { roomService.deleteRoom("not a real UUID") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToDeleteRoomWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> { roomService.deleteRoom(UUID.randomUUID().toString()) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // Private message rooms
    // ==========================
    @Test
    fun shouldCreatePrivateMessageRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntity("us", any()) } returns UserEntity(username = "us", password = "")
        every { roomRepository.findById(any()) } returns Optional.empty()
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val roomId = roomService.getOrStartPrivateMessage("us")

        assertNotNull(roomId)
        verify(exactly = 1) { roomRepository.save(any()) }
        verify(exactly = 2) { userRoomRepository.save(any()) }
    }

    @Test
    fun shouldGetPrivateMessageRoom() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntity("us", any()) } returns UserEntity(username = "us", password = "")
        every { roomRepository.findById(any()) } returns Optional.of(RoomEntity(id = UUID.randomUUID(), name = "room", type = RoomType.GROUP))
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        val roomId = roomService.getOrStartPrivateMessage("us")

        assertNotNull(roomId)
        verify(exactly = 0) { roomRepository.save(any()) }
        verify(exactly = 0) { userRoomRepository.save(any()) }
    }

    @Test
    fun shouldFailToGetPrivateMessagesWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> { roomService.getOrStartPrivateMessage("some user") }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetPrivateMessagesWhenFriendNotFound() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")
        every { friendService.getFriendEntity("some user", any()) } throws UserNotFoundException()

        val exception = assertFailsWith<ApiException> { roomService.getOrStartPrivateMessage("some user") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetPrivateMessagesWithYourself() {
        val user = UserEntity(username = "u", password = "")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user.id, null, emptyList())

        every { userService.getUserById(user.id) } returns user
        every { friendService.getFriendEntity("u", any()) } returns user
        every { roomRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> { roomService.getOrStartPrivateMessage("u") }
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
                actions = RoomAction.KICK,
                reason = ""
            )
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
                actions = RoomAction.BAN,
                reason = ""
            )
        )

        verify(exactly = 1) { userRoomRepository.deleteByIdUserIdAndIdRoomId(targetId, roomId) }
        verify(exactly = 1) { bannedUserService.banUser(targetId, roomId) }
        verify(exactly = 1) { eventPublisher.publishEvent(any<Any>()) }
    }

    @Test
    fun shouldFailToRemoveUserWhenNotInRoom() {
        val userId = UUID.randomUUID()

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.removeUserFromRoom(
                AdministrationDTO(
                    roomId = UUID.randomUUID().toString(),
                    userId = UUID.randomUUID().toString(),
                    actions = RoomAction.KICK,
                    reason = ""
                )
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

        val exception = assertFailsWith<ApiException> {
            roomService.removeUserFromRoom(
                AdministrationDTO(
                    roomId = roomId.toString(),
                    userId = UUID.randomUUID().toString(),
                    actions = RoomAction.KICK,
                    reason = ""
                )
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

        val exception = assertFailsWith<ApiException> {
            roomService.removeUserFromRoom(
                AdministrationDTO(
                    roomId = "not-a-uuid",
                    userId = UUID.randomUUID().toString(),
                    actions = RoomAction.KICK,
                    reason = ""
                )
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

        val exception = assertFailsWith<ApiException> {
            roomService.removeUserFromRoom(
                AdministrationDTO(
                    roomId = UUID.randomUUID().toString(),
                    userId = "not-a-uuid",
                    actions = RoomAction.KICK,
                    reason = ""
                )
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

        val exception = assertFailsWith<ApiException> {
            roomService.removeUserFromRoom(
                AdministrationDTO(
                    roomId = roomId.toString(),
                    userId = userId.toString(),
                    actions = RoomAction.KICK,
                    reason = ""
                )
            )
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhenInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userService.getUserById(any()) } returns null

        val exception = assertFailsWith<ApiException> {
            roomService.removeUserFromRoom(
                AdministrationDTO(
                    roomId = UUID.randomUUID().toString(),
                    userId = UUID.randomUUID().toString(),
                    actions = RoomAction.KICK,
                    reason = ""
                )
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
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

        val exception = assertFailsWith<ApiException> { roomService.deleteRoom(roomId.toString()) }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldGenerateSamePrivateRoomForBothUserOrders() {
        val low = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
        val userLow = UserEntity(id = low, username = "low", password = "")
        val userHigh = UserEntity(id = high, username = "high", password = "")

        every { userService.getUserById(low) } returns userLow
        every { userService.getUserById(high) } returns userHigh
        every { friendService.getFriendEntity("high", low) } returns userHigh
        every { friendService.getFriendEntity("low", high) } returns userLow
        every { roomRepository.findById(any()) } returns Optional.empty()
        every { roomRepository.save(any()) } answers { firstArg() }
        every { userRoomRepository.save(any()) } answers { firstArg() }

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(low, null, emptyList())
        val roomId1 = roomService.getOrStartPrivateMessage("high")

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(high, null, emptyList())
        val roomId2 = roomService.getOrStartPrivateMessage("low")

        assertEquals(roomId1, roomId2)
    }
}