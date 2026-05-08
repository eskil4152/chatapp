package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.InvitesController
import com.blikeng.chatapp.dtos.invites.PendingInviteDTO
import com.blikeng.chatapp.entities.InviteType
import com.blikeng.chatapp.errors.AlreadyFriendsException
import com.blikeng.chatapp.errors.AlreadyInvitedException
import com.blikeng.chatapp.errors.InvalidInviteException
import com.blikeng.chatapp.errors.InviteNotFoundException
import com.blikeng.chatapp.errors.NotPermittedException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitingService
import com.blikeng.chatapp.services.InviteService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant
import java.util.UUID

@WebMvcTest(
    controllers = [InvitesController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class],
        ),
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class InvitesControllerTests {
    // ==========================
    // Tests for InvitesController. Verifies:
    // - GET /pending returns pending invite list
    // - POST /friend returns success message
    // - POST /room returns success message
    // - POST /open returns the created invite ID
    // - POST /respond returns success message
    // - HTTP error mapping for service exceptions
    // ==========================

    @MockkBean private lateinit var inviteService: InviteService

    @MockkBean private lateinit var rateLimitingService: RateLimitingService

    @Autowired private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setup() {
        every { rateLimitingService.tryConsume(any(), any(), any()) } returns true
    }

    @Test
    fun shouldGetPendingInvites() {
        val invite =
            PendingInviteDTO(
                id = UUID.randomUUID(),
                type = InviteType.FRIEND_REQUEST,
                fromUserId = UUID.randomUUID(),
                roomId = null,
                expiresAt = Instant.now(),
                fromUsername = "user1",
                fromAvatarUrl = null,
                roomName = null,
            )
        every { inviteService.getPendingInvites() } returns listOf(invite)

        mockMvc
            .get("/api/invites/pending")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[0].type") { value("FRIEND_REQUEST") } }
    }

    @Test
    fun shouldReturnEmptyListWhenNoPendingInvites() {
        every { inviteService.getPendingInvites() } returns emptyList()

        mockMvc
            .get("/api/invites/pending")
            .andExpect { status { isOk() } }
            .andExpect { content { string("[]") } }
    }

    @Test
    fun shouldSendFriendRequest() {
        every { inviteService.sendFriendRequest(any()) } returns Unit

        mockMvc
            .post("/api/invites/friend") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"someuser"}"""
            }.andExpect { status { isOk() } }
            .andExpect { content { string("Friend request sent successfully") } }
    }

    @Test
    fun shouldSendRoomInvite() {
        every { inviteService.sendRoomInvite(any()) } returns Unit

        mockMvc
            .post("/api/invites/room") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"type":"ROOM_INVITE","targetUsername":"username","roomId":"${UUID.randomUUID()}","expiresAt":${System.currentTimeMillis() + 604800000}}"""
            }.andExpect { status { isOk() } }
            .andExpect { content { string("Room invite sent successfully") } }
    }

    @Test
    fun shouldCreateOpenRoomInviteAndReturnId() {
        val inviteId = UUID.randomUUID()
        every { inviteService.createOpenRoomInvite(any()) } returns inviteId

        mockMvc
            .post("/api/invites/open") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"type":"OPEN_ROOM_INVITE","roomId":"${UUID.randomUUID()}","maxUsages":5,"expiresAt":${System.currentTimeMillis() + 604800000}}"""
            }.andExpect { status { isOk() } }
            .andExpect { content { string(inviteId.toString()) } }
    }

    @Test
    fun shouldRespondToInvite() {
        every { inviteService.respondToRequest(any()) } returns Unit

        mockMvc
            .post("/api/invites/respond") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"inviteId":"${UUID.randomUUID()}","response":"ACCEPTED"}"""
            }.andExpect { status { isOk() } }
            .andExpect { content { string("Invite responded successfully") } }
    }

    // ==========================
    // HTTP error mapping
    // ==========================
    @Test
    fun shouldReturnNotFoundWhenUserNotFound() {
        every { inviteService.sendFriendRequest(any()) } throws UserNotFoundException()

        mockMvc
            .post("/api/invites/friend") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"ghost"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun shouldReturnConflictWhenAlreadyInvited() {
        every { inviteService.sendFriendRequest(any()) } throws AlreadyInvitedException()

        mockMvc
            .post("/api/invites/friend") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"someone"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun shouldReturnConflictWhenAlreadyFriends() {
        every { inviteService.sendFriendRequest(any()) } throws AlreadyFriendsException()

        mockMvc
            .post("/api/invites/friend") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"someone"}"""
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun shouldReturnForbiddenWhenNotPermitted() {
        every { inviteService.sendRoomInvite(any()) } throws NotPermittedException()

        mockMvc
            .post("/api/invites/room") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"type":"ROOM_INVITE","targetUsername":"username","roomId":"${UUID.randomUUID()}","expiresAt":${System.currentTimeMillis() + 604800000}}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun shouldReturnNotFoundWhenInviteNotFound() {
        every { inviteService.respondToRequest(any()) } throws InviteNotFoundException()

        mockMvc
            .post("/api/invites/respond") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"inviteId":"${UUID.randomUUID()}","response":"ACCEPTED"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun shouldReturnBadRequestForInvalidInvite() {
        every { inviteService.createOpenRoomInvite(any()) } throws InvalidInviteException()

        mockMvc
            .post("/api/invites/open") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"type":"OPEN_ROOM_INVITE","roomId":"${UUID.randomUUID()}","maxUsages":0,"expiresAt":${System.currentTimeMillis() + 604800000}}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun shouldGetOutgoingInvites() {
        every { inviteService.getOutgoingInvites() } returns emptyList()

        mockMvc
            .get("/api/invites/outgoing")
            .andExpect { status { isOk() } }
            .andExpect { content { string("[]") } }
    }
}
