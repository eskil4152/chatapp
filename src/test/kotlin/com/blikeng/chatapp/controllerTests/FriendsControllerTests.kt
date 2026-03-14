package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.FriendsController
import com.blikeng.chatapp.dtos.friends.FriendDTO
import com.blikeng.chatapp.errors.AlreadyFriendsException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import com.blikeng.chatapp.services.FriendsService
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.sql.Date
import java.time.Instant

@WebMvcTest(
    controllers = [FriendsController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class FriendsControllerTests {
    // ==========================
    // Tests for FriendsController. Verifies:
    // - Retrieving friends
    // - Adding friends
    // - Removing friends
    // - Retrieving friend information
    // - HTTP error mapping for service exceptions
    // ==========================

    @MockkBean private lateinit var friendsService: FriendsService

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setup() {
        every { rateLimitService.tryConsume(any(), any(), any()) } returns true
    }

    val friend = FriendDTO(
        "username",
        "bio",
        "email",
        "full name",
        "",
        Date.from(Instant.now()),
        Date.from(Instant.now()),
    )

    @Test
    fun shouldGetFriends(){
        every { friendsService.getFriends() } returns listOf(friend)

        mockMvc.get("/api/friends") {
            contentType = MediaType.APPLICATION_JSON
        }
            .andExpect {
                status { isOk() }
                content { string(org.hamcrest.Matchers.containsString("username")) }
            }
    }

    @Test
    fun shouldAddFriends(){
        every { friendsService.addFriend("friend") } returns Unit

        mockMvc.post("/api/friends/add") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username": "friend"
                }
            """.trimIndent()
        }.andExpect {  status { isOk() } }
    }

    @Test
    fun shouldRemoveFriends(){
        every { friendsService.removeFriend("friend") } returns Unit

        mockMvc.delete("/api/friends/remove") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username": "friend"
                }
            """.trimIndent()
        }.andExpect {  status { isOk() } }
    }

    @Test
    fun shouldGetFriendsInfo(){
        every { friendsService.getFriendInfo("username") } returns friend

        mockMvc.get("/api/friends/username") {
            contentType = MediaType.APPLICATION_JSON
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.username") {
                    value("username")
                }
                jsonPath("$.bio") {
                    value("bio")
                }
            }
    }

    // ==========================
    // HTTP error mapping
    // ==========================
    @Test
    fun shouldGetBadRequestFromInvalidUser(){
        every { friendsService.getFriends() } throws InvalidUserException()

        mockMvc.get("/api/friends") {
            contentType = MediaType.APPLICATION_JSON
        }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun shouldGetNotFoundUser(){
        every { friendsService.getFriendInfo("fakeuser") } throws UserNotFoundException()

        mockMvc.get("/api/friends/fakeuser")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun shouldGetConflictFromBeingAlreadyFriends(){
        every { friendsService.addFriend("myself") } throws AlreadyFriendsException()

        mockMvc.post("/api/friends/add"){
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username": "myself"
                }
            """.trimIndent()
        }.andExpect { status { isConflict() } }
    }
}