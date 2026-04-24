package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.AdministrationController
import com.blikeng.chatapp.dtos.administration.AdvancedSiteInfoDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDTO
import com.blikeng.chatapp.dtos.administration.HttpEndpointMetric
import com.blikeng.chatapp.dtos.administration.HttpStatusCount
import com.blikeng.chatapp.dtos.administration.SiteInfoDTO
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

    // ==========================
    // getSiteInfo
    // ==========================
    @Test
    fun shouldGetSiteInfo() {
        val dto = SiteInfoDTO(
            connectedUsers = 5.0, totalSessions = 8.0, activeRooms = 3.0,
            totalUsers = 100L, totalRooms = 20L, bannedUsers = 2L
        )
        every { administrationService.getSiteInfo() } returns dto

        mockMvc.get("/api/admin/site-info")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.connectedUsers") { value(5.0) } }
            .andExpect { jsonPath("$.totalSessions") { value(8.0) } }
            .andExpect { jsonPath("$.activeRooms") { value(3.0) } }
            .andExpect { jsonPath("$.totalUsers") { value(100) } }
            .andExpect { jsonPath("$.totalRooms") { value(20) } }
            .andExpect { jsonPath("$.bannedUsers") { value(2) } }
    }

    @Test
    fun shouldGetUnauthorizedOnSiteInfoWhenTokenInvalid() {
        every { administrationService.getSiteInfo() } throws InvalidTokenException()

        mockMvc.get("/api/admin/site-info")
            .andExpect { status { isUnauthorized() } }
    }

    // ==========================
    // getAdvancedSiteInfo
    // ==========================
    @Test
    fun shouldGetAdvancedSiteInfo() {
        val dto = AdvancedSiteInfoDTO(
            jvmMemoryUsedMb = 256.0, jvmMemoryMaxMb = 512.0, jvmMemoryCommittedMb = 384.0,
            jvmThreadsLive = 20, jvmThreadsPeak = 25,
            cpuUsagePercent = 42.0,
            gcPauseMeanMs = 1.5, gcPauseMaxMs = 10.0,
            uptimeSeconds = 3600L,
            httpRequests = listOf(
                HttpEndpointMetric(
                    uri = "/api/rooms", method = "GET",
                    statuses = listOf(HttpStatusCount(200, 50L)),
                    totalCount = 50L, errorRate = 0.0,
                    meanMs = 12.0, maxMs = 100.0
                )
            )
        )
        every { administrationService.getAdvancedSiteInfo() } returns dto

        mockMvc.get("/api/admin/advanced-site-info")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.cpuUsagePercent") { value(42.0) } }
            .andExpect { jsonPath("$.jvmThreadsLive") { value(20) } }
            .andExpect { jsonPath("$.uptimeSeconds") { value(3600) } }
            .andExpect { jsonPath("$.httpRequests[0].uri") { value("/api/rooms") } }
            .andExpect { jsonPath("$.httpRequests[0].method") { value("GET") } }
            .andExpect { jsonPath("$.httpRequests[0].totalCount") { value(50) } }
            .andExpect { jsonPath("$.httpRequests[0].errorRate") { value(0.0) } }
            .andExpect { jsonPath("$.httpRequests[0].statuses[0].status") { value(200) } }
            .andExpect { jsonPath("$.httpRequests[0].statuses[0].count") { value(50) } }
    }

    @Test
    fun shouldGetUnauthorizedOnAdvancedSiteInfoWhenTokenInvalid() {
        every { administrationService.getAdvancedSiteInfo() } throws InvalidTokenException()

        mockMvc.get("/api/admin/advanced-site-info")
            .andExpect { status { isUnauthorized() } }
    }
}
