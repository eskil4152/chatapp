package com.blikeng.chatapp.serviceTests.roomServiceTests

import com.blikeng.chatapp.dtos.room.ChangeRoleDTO
import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.services.BannedUserService
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
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
import java.util.UUID

@ExtendWith(MockKExtension::class)
class RoomServiceRoleTests {
    // ==========================
    // Tests for RoomService.changeRole.
    // Verifies promotion and demotion paths for all role transitions,
    // plus auth, UUID, ownership, and permission failure cases.
    // ==========================

    @InjectMockKs lateinit var roomService: RoomService

    @MockK private lateinit var roomRepository: RoomRepository

    @MockK private lateinit var userService: UserService

    @MockK private lateinit var userRoomRepository: UserRoomRepository

    @MockK private lateinit var friendService: FriendService

    @MockK private lateinit var bannedUserService: BannedUserService

    @RelaxedMockK private lateinit var eventPublisher: ApplicationEventPublisher

    @RelaxedMockK private lateinit var redisTemplate: RedisTemplate<String, String>

    @RelaxedMockK private lateinit var objectMapper: ObjectMapper

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

    private fun setAuth(userId: UUID) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    private fun userRoom(
        userId: UUID,
        roomId: UUID,
        role: RoomRole,
    ) = UserRoomEntity(id = UserRoomId(userId, roomId), role = role, type = RoomType.GROUP)

    // ==========================
    // Successful promotions
    // ==========================
    @Test
    fun shouldPromoteMemberToModerator() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        val requesterRoom = userRoom(userId, roomId, RoomRole.ADMIN)
        val targetRoom = userRoom(targetId, roomId, RoomRole.MEMBER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns requesterRoom
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns targetRoom
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))

        verify { userRoomRepository.save(match { it.role == RoomRole.MODERATOR }) }
    }

    @Test
    fun shouldPromoteModeratorToAdmin() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        val requesterRoom = userRoom(userId, roomId, RoomRole.OWNER)
        val targetRoom = userRoom(targetId, roomId, RoomRole.MODERATOR)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns requesterRoom
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns targetRoom
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))

        verify { userRoomRepository.save(match { it.role == RoomRole.ADMIN }) }
    }

    // ==========================
    // Successful demotions
    // ==========================
    @Test
    fun shouldDemoteAdminToModerator() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        val requesterRoom = userRoom(userId, roomId, RoomRole.OWNER)
        val targetRoom = userRoom(targetId, roomId, RoomRole.ADMIN)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns requesterRoom
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns targetRoom
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.DEMOTE))

        verify { userRoomRepository.save(match { it.role == RoomRole.MODERATOR }) }
    }

    @Test
    fun shouldDemoteModeratorToMember() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        val requesterRoom = userRoom(userId, roomId, RoomRole.ADMIN)
        val targetRoom = userRoom(targetId, roomId, RoomRole.MODERATOR)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns requesterRoom
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns targetRoom
        every { userRoomRepository.save(any()) } answers { firstArg() }

        roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.DEMOTE))

        verify { userRoomRepository.save(match { it.role == RoomRole.MEMBER }) }
    }

    // ==========================
    // Failure cases
    // ==========================
    @Test
    fun shouldFailToChangeRoleWithInvalidRoomId() {
        setAuth(UUID.randomUUID())
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(
                    ChangeRoleDTO(userId = UUID.randomUUID().toString(), roomId = "not-a-uuid", action = RoleAction.PROMOTE),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWithInvalidTargetId() {
        setAuth(UUID.randomUUID())
        every { userService.getUserById(any()) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(
                    ChangeRoleDTO(userId = "not-a-uuid", roomId = UUID.randomUUID().toString(), action = RoleAction.PROMOTE),
                )
            }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleOnSelf() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = userId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWhenRequesterNotInRoom() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns null

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))
            }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWhenTargetNotInRoom() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.OWNER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns null

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))
            }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWhenTargetIsOwner() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.OWNER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.OWNER)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.DEMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToPromoteWithInsufficientPermission() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        // MODERATOR cannot promote anyone (PROMOTE_TO_MODERATOR requires ADMIN)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.MODERATOR)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.MEMBER)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToPromoteAdmin() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.OWNER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.ADMIN)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDemoteMember() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.OWNER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.MEMBER)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.DEMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToPromoteModeratorToAdminWithInsufficientPermission() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        // ADMIN cannot promote MODERATOR to ADMIN (PROMOTE_TO_ADMIN requires OWNER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.ADMIN)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.MODERATOR)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.PROMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDemoteAdminToModeratorWithInsufficientPermission() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        // ADMIN cannot demote ADMIN to MODERATOR (DEMOTE_TO_MODERATOR requires OWNER)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.ADMIN)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.ADMIN)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.DEMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToDemoteModeratorToMemberWithInsufficientPermission() {
        val userId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        setAuth(userId)
        every { userService.getUserById(userId) } returns UserEntity(username = "u", password = "")
        // MODERATOR cannot demote MODERATOR to MEMBER (DEMOTE_TO_MEMBER requires ADMIN)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(userId, roomId) } returns userRoom(userId, roomId, RoomRole.MODERATOR)
        every { userRoomRepository.findByIdUserIdAndIdRoomId(targetId, roomId) } returns userRoom(targetId, roomId, RoomRole.MODERATOR)

        val exception =
            assertThrows<ApiException> {
                roomService.changeRole(ChangeRoleDTO(userId = targetId.toString(), roomId = roomId.toString(), action = RoleAction.DEMOTE))
            }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }
}
