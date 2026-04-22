package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.AdministrationController
import com.blikeng.chatapp.dtos.administration.ElevatedUserDTO
import com.blikeng.chatapp.dtos.administration.UserDetailDTO
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.NotPermittedException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.security.UserRole
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import com.blikeng.chatapp.services.AdministrationService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Test

@WebMvcTest(
    controllers = [AdministrationController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class AdministrationControllerTests {
    // ==========================
    // Tests for AdministrationController. Verifies:
    // - Getting elevated users
    // - Getting user detail by ID
    // - Changing user role
    // - Banning a user
    // - Unbanning a user
    // - Getting paginated banned users list
    // - HTTP error mapping for service exceptions
    // ==========================

    @MockkBean private lateinit var administrationService: AdministrationService
    @MockkBean private lateinit var rateLimitService: RateLimitService
    @Autowired private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        every { rateLimitService.tryConsume(any(), any(), any()) } returns true
    }

    @Test
    fun shouldGetElevatedUsers() {
        val user = ElevatedUserDTO(
            id = UUID.randomUUID(), username = "admin", avatarUrl = null,
            role = UserRole.ADMIN, createdAt = Instant.now()
        )
        every { administrationService.getElevatedUsers() } returns listOf(user)

        mockMvc.get("/api/admin/users")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[0].username") { value("admin") } }
            .andExpect { jsonPath("$[0].role") { value("ADMIN") } }
    }

    @Test
    fun shouldGetEmptyListWhenNoElevatedUsers() {
        every { administrationService.getElevatedUsers() } returns emptyList()

        mockMvc.get("/api/admin/users")
            .andExpect { status { isOk() } }
            .andExpect { content { json("[]") } }
    }

    @Test
    fun shouldGetUserById() {
        val userId = UUID.randomUUID()
        val detail = UserDetailDTO(
            id = userId, username = "alice", bio = null, email = null,
            fullName = null, avatarUrl = null, role = UserRole.USER,
            createdAt = Instant.now(), rooms = null
        )
        every { administrationService.getUser(userId.toString()) } returns detail

        mockMvc.get("/api/admin/user/$userId")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.username") { value("alice") } }
            .andExpect { jsonPath("$.role") { value("USER") } }
    }

    @Test
    fun shouldChangeUserRole() {
        every { administrationService.changeUserRole(any()) } returns Unit

        mockMvc.post("/api/admin/change-user-role") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"${UUID.randomUUID()}","action":"PROMOTE"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Role updated successfully") } }
    }

    @Test
    fun shouldBanUser() {
        every { administrationService.banUser(any()) } returns Unit

        mockMvc.post("/api/admin/ban-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"${UUID.randomUUID()}","reason":"spam"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Banned user") } }
    }

    @Test
    fun shouldUnbanUser() {
        every { administrationService.unbanUser(any()) } returns Unit

        mockMvc.post("/api/admin/unban-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"${UUID.randomUUID()}"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Unbanned user") } }
    }

    @Test
    fun shouldGetBannedUsers() {
        every { administrationService.getAllUserBans(0, 25) } returns emptyList()

        mockMvc.get("/api/admin/banned")
            .andExpect { status { isOk() } }
            .andExpect { content { json("[]") } }
    }

    @Test
    fun shouldGetBannedUsersWithCustomPagination() {
        every { administrationService.getAllUserBans(1, 50) } returns emptyList()

        mockMvc.get("/api/admin/banned?page=1&size=50")
            .andExpect { status { isOk() } }
    }

    // ==========================
    // HTTP error mapping
    // ==========================
    @Test
    fun shouldGetUnauthorizedWhenTokenInvalid() {
        every { administrationService.getElevatedUsers() } throws InvalidTokenException()

        mockMvc.get("/api/admin/users")
            .andExpect { status { isUnauthorized() } }
            .andExpect { content { string("Invalid token") } }
    }

    @Test
    fun shouldGetForbiddenWhenNotPermittedToChangeRole() {
        every { administrationService.changeUserRole(any()) } throws NotPermittedException()

        mockMvc.post("/api/admin/change-user-role") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"${UUID.randomUUID()}","action":"PROMOTE"}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { content { string("Not permitted") } }
    }

    @Test
    fun shouldGetNotFoundWhenUserNotFound() {
        val userId = UUID.randomUUID()
        every { administrationService.getUser(userId.toString()) } throws UserNotFoundException()

        mockMvc.get("/api/admin/user/$userId")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun shouldGetForbiddenWhenNotPermittedToBan() {
        every { administrationService.banUser(any()) } throws NotPermittedException()

        mockMvc.post("/api/admin/ban-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"id":"${UUID.randomUUID()}"}"""
        }
            .andExpect { status { isForbidden() } }
            .andExpect { content { string("Not permitted") } }
    }

    @Test
    fun shouldGetForbiddenWhenNotPermittedToUnban() {
        every { administrationService.unbanUser(any()) } throws NotPermittedException()

        mockMvc.post("/api/admin/unban-user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"${UUID.randomUUID()}"}"""
        }
            .andExpect { status { isForbidden() } }
    }
}
