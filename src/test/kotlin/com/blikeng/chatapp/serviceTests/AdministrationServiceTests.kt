package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.administration.BanUserDTO
import com.blikeng.chatapp.dtos.administration.UserRoleDTO
import com.blikeng.chatapp.repositories.BanProjection
import java.time.Instant
import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.repositories.UserBanRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.UserRole
import com.blikeng.chatapp.services.AdministrationService
import com.blikeng.chatapp.services.UserRevocationService
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher

@ExtendWith(MockKExtension::class)
class AdministrationServiceTests {
    // ==========================
    // Tests for AdministrationService. Verifies:
    // - getElevatedUsers: returns list and guards caller validity
    // - getUser: returns user detail and guards UUID format, caller validity, user existence
    // - changeUserRole: promotes/demotes and enforces role hierarchy
    // - banUser: saves a ban and guards role hierarchy, duplicate bans, invalid input
    // - unbanUser: removes a ban and guards banner-rank check, invalid input
    // - getAllUserBans: returns paginated list and validates page/size parameters
    // ==========================

    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var userBanRepository: UserBanRepository
    @RelaxedMockK private lateinit var eventPublisher: ApplicationEventPublisher
    @MockK private lateinit var userRevocationService: UserRevocationService

    @InjectMockKs private lateinit var administrationService: AdministrationService

    @AfterEach
    fun clearSecurity() { SecurityContextHolder.clearContext() }

    private fun authenticateAs(userId: UUID) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())
    }

    // ==========================
    // getElevatedUsers
    // ==========================
    @Test
    fun shouldReturnElevatedUsers() {
        val elevated = UserEntity(username = "mod", password = "", role = UserRole.MODERATOR)

        every { userRepository.findAllByRoleNot(any()) } returns listOf(elevated)

        val result = administrationService.getElevatedUsers()

        assertEquals(1, result.size)
        assertEquals("mod", result[0].username)
        assertEquals(UserRole.MODERATOR, result[0].role)
    }

    // ==========================
    // getUser
    // ==========================
    @Test
    fun shouldReturnUserDetail() {
        val targetId = UUID.randomUUID()
        val target = UserEntity(id = targetId, username = "alice", password = "", role = UserRole.USER)

        every { userRepository.findByUsername("alice") } returns Optional.of(target)

        val result = administrationService.getUser("alice")

        assertEquals(targetId, result.id)
        assertEquals("alice", result.username)
        assertEquals(UserRole.USER, result.role)
    }

    @Test
    fun shouldFailToGetUserWhenTargetNotFound() {
        every { userRepository.findByUsername(any()) } returns Optional.empty()

        val exception = assertThrows<ApiException> { administrationService.getUser("alice") }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    // ==========================
    // changeUserRole
    // ==========================
    @Test
    fun shouldPromoteUser() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "superuser", password = "", role = UserRole.SUPERUSER)
        val target = UserEntity(id = targetId, username = "alice", password = "", role = UserRole.USER)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any()) } just Runs

        administrationService.changeUserRole(UserRoleDTO(id = targetId.toString(), action = RoleAction.PROMOTE))

        verify(exactly = 1) { userRepository.save(match { it.role == UserRole.TRUSTED }) }
    }

    @Test
    fun shouldDemoteUser() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "superuser", password = "", role = UserRole.SUPERUSER)
        val target = UserEntity(id = targetId, username = "mod", password = "", role = UserRole.MODERATOR)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)
        every { userRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any()) } just Runs

        administrationService.changeUserRole(UserRoleDTO(id = targetId.toString(), action = RoleAction.DEMOTE))

        verify(exactly = 1) { userRepository.save(match { it.role == UserRole.TRUSTED }) }
    }

    @Test
    fun shouldFailToChangeRoleWhenCallerRoleNotHigherThanTarget() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "mod", password = "", role = UserRole.MODERATOR)
        val target = UserEntity(id = targetId, username = "admin", password = "", role = UserRole.ADMIN)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)

        val exception = assertThrows<ApiException> {
            administrationService.changeUserRole(UserRoleDTO(id = targetId.toString(), action = RoleAction.DEMOTE))
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWhenCallerHasSameRoleAsTarget() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "mod1", password = "", role = UserRole.MODERATOR)
        val target = UserEntity(id = targetId, username = "mod2", password = "", role = UserRole.MODERATOR)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)

        val exception = assertThrows<ApiException> {
            administrationService.changeUserRole(UserRoleDTO(id = targetId.toString(), action = RoleAction.DEMOTE))
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWithInvalidTargetUUID() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )

        val exception = assertThrows<ApiException> {
            administrationService.changeUserRole(UserRoleDTO(id = "not-a-uuid", action = RoleAction.PROMOTE))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWhenTargetNotFound() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )
        every { userRepository.findById(targetId) } returns Optional.empty()

        val exception = assertThrows<ApiException> {
            administrationService.changeUserRole(UserRoleDTO(id = targetId.toString(), action = RoleAction.PROMOTE))
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToChangeRoleWhenCallerNotFound() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.empty()

        val exception = assertThrows<ApiException> {
            administrationService.changeUserRole(UserRoleDTO(id = UUID.randomUUID().toString(), action = RoleAction.PROMOTE))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // banUser
    // ==========================
    @Test
    fun shouldBanUser() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        val target = UserEntity(id = targetId, username = "alice", password = "", role = UserRole.USER)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)
        every { userBanRepository.existsById(targetId) } returns false
        every { userBanRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any()) } just Runs

        administrationService.banUser(BanUserDTO(id = targetId.toString(), reason = "rule violation"))

        verify(exactly = 1) {
            userBanRepository.save(match { it.userId == targetId && it.bannedBy == callerId && it.reason == "rule violation" })
        }
    }

    @Test
    fun shouldBanUserWithoutReason() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        val target = UserEntity(id = targetId, username = "alice", password = "", role = UserRole.USER)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)
        every { userBanRepository.existsById(targetId) } returns false
        every { userBanRepository.save(any()) } answers { firstArg() }
        every { eventPublisher.publishEvent(any()) } just Runs

        administrationService.banUser(BanUserDTO(id = targetId.toString(), reason = null))

        verify(exactly = 1) {
            userBanRepository.save(match { it.userId == targetId && it.bannedBy == callerId && it.reason == null })
        }
    }

    @Test
    fun shouldFailToBanWhenAlreadyBanned() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        val target = UserEntity(id = targetId, username = "alice", password = "", role = UserRole.USER)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)
        every { userBanRepository.existsById(targetId) } returns true

        val exception = assertThrows<ApiException> {
            administrationService.banUser(BanUserDTO(id = targetId.toString()))
        }
        assertEquals(HttpStatus.CONFLICT, exception.status)
    }

    @Test
    fun shouldFailToBanWhenCallerRoleNotHigherThanTarget() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "mod", password = "", role = UserRole.MODERATOR)
        val target = UserEntity(id = targetId, username = "admin", password = "", role = UserRole.ADMIN)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userRepository.findById(targetId) } returns Optional.of(target)

        val exception = assertThrows<ApiException> {
            administrationService.banUser(BanUserDTO(id = targetId.toString()))
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToBanWithInvalidTargetUUID() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )

        val exception = assertThrows<ApiException> {
            administrationService.banUser(BanUserDTO(id = "not-a-uuid"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToBanWhenTargetNotFound() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )
        every { userRepository.findById(targetId) } returns Optional.empty()

        val exception = assertThrows<ApiException> {
            administrationService.banUser(BanUserDTO(id = targetId.toString()))
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToBanWhenCallerNotFound() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.empty()

        val exception = assertThrows<ApiException> {
            administrationService.banUser(BanUserDTO(id = UUID.randomUUID().toString()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // unbanUser
    // ==========================
    @Test
    fun shouldUnbanUser() {
        val callerId = UUID.randomUUID()
        val bannerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "superuser", password = "", role = UserRole.SUPERUSER)
        val banner = UserEntity(id = bannerId, username = "admin", password = "", role = UserRole.ADMIN)
        val ban = BannedUser(userId = targetId, bannedBy = bannerId)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userBanRepository.findById(targetId) } returns Optional.of(ban)
        every { userRepository.findById(bannerId) } returns Optional.of(banner)
        every { userBanRepository.delete(ban) } just Runs
        every { userRevocationService.unRevokeBanned(targetId) } just Runs

        administrationService.unbanUser(UserIdDTO(userId = targetId.toString()))

        verify(exactly = 1) { userBanRepository.delete(ban) }
    }

    @Test
    fun shouldUnbanUserWhenBannerNoLongerExists() {
        val callerId = UUID.randomUUID()
        val bannerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        val ban = BannedUser(userId = targetId, bannedBy = bannerId)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userBanRepository.findById(targetId) } returns Optional.of(ban)
        every { userRepository.findById(bannerId) } returns Optional.empty()
        every { userBanRepository.delete(ban) } just Runs
        every { userRevocationService.unRevokeBanned(targetId) } just Runs

        administrationService.unbanUser(UserIdDTO(userId = targetId.toString()))

        verify(exactly = 1) { userBanRepository.delete(ban) }
    }

    @Test
    fun shouldFailToUnbanWhenNotBanned() {
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )
        every { userBanRepository.findById(targetId) } returns Optional.empty()

        val exception = assertThrows<ApiException> {
            administrationService.unbanUser(UserIdDTO(userId = targetId.toString()))
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToUnbanWhenCallerRankLowerThanBanner() {
        val callerId = UUID.randomUUID()
        val bannerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        authenticateAs(callerId)

        val caller = UserEntity(id = callerId, username = "mod", password = "", role = UserRole.MODERATOR)
        val banner = UserEntity(id = bannerId, username = "admin", password = "", role = UserRole.ADMIN)
        val ban = BannedUser(userId = targetId, bannedBy = bannerId)

        every { userRepository.findById(callerId) } returns Optional.of(caller)
        every { userBanRepository.findById(targetId) } returns Optional.of(ban)
        every { userRepository.findById(bannerId) } returns Optional.of(banner)

        val exception = assertThrows<ApiException> {
            administrationService.unbanUser(UserIdDTO(userId = targetId.toString()))
        }
        assertEquals(HttpStatus.FORBIDDEN, exception.status)
    }

    @Test
    fun shouldFailToUnbanWithInvalidTargetUUID() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )

        val exception = assertThrows<ApiException> {
            administrationService.unbanUser(UserIdDTO(userId = "not-a-uuid"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToUnbanWhenCallerNotFound() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.empty()

        val exception = assertThrows<ApiException> {
            administrationService.unbanUser(UserIdDTO(userId = UUID.randomUUID().toString()))
        }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    // ==========================
    // getAllUserBans
    // ==========================
    @Test
    fun shouldReturnPaginatedBannedUsers() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        every { userRepository.findById(callerId) } returns Optional.of(
            UserEntity(id = callerId, username = "admin", password = "", role = UserRole.ADMIN)
        )
        every { userBanRepository.findAllWithUsers(PageRequest.of(0, 25)) } returns PageImpl(emptyList())

        val result = administrationService.getAllUserBans(0, 25)

        assertEquals(0, result.size)
    }

    @Test
    fun shouldReturnMappedBannedUsers() {
        val bannedId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val now = Instant.now()

        val projection = mockk<BanProjection>()
        every { projection.userId } returns bannedId
        every { projection.username } returns "alice"
        every { projection.bannedBy } returns adminId
        every { projection.bannedByUsername } returns "admin"
        every { projection.bannedByRole } returns UserRole.ADMIN
        every { projection.bannedAt } returns now
        every { projection.reason } returns "spam"

        every { userBanRepository.findAllWithUsers(PageRequest.of(0, 25)) } returns PageImpl(listOf(projection))

        val result = administrationService.getAllUserBans(0, 25)

        assertEquals(1, result.size)
        assertEquals(bannedId, result[0].userId)
        assertEquals("alice", result[0].username)
        assertEquals(adminId, result[0].bannedBy)
        assertEquals("admin", result[0].bannedByUsername)
        assertEquals(UserRole.ADMIN, result[0].bannedByRole)
        assertEquals(now, result[0].bannedAt)
        assertEquals("spam", result[0].reason)
    }

    @Test
    fun shouldFailToGetBannedUsersWithInvalidPageSize() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        val exception = assertThrows<ApiException> { administrationService.getAllUserBans(0, 10) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetBannedUsersWithNegativePage() {
        val callerId = UUID.randomUUID()
        authenticateAs(callerId)

        val exception = assertThrows<ApiException> { administrationService.getAllUserBans(-1, 25) }
        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

}
