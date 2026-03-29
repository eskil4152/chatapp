package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.UserController
import com.blikeng.chatapp.dtos.user.UserDTO
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.WrongPasswordException
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import com.blikeng.chatapp.services.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.sql.Date
import kotlin.test.Test

@WebMvcTest(
    controllers = [UserController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTests {
    // ==========================
    // Tests for UserController. Verifies:
    // - Retrieving the current user
    // - Updating user profile information
    // - Updating user password
    // - HTTP error mapping for service exceptions
    // ==========================

    @MockkBean private lateinit var userService: UserService
    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setup() {
        every { rateLimitService.tryConsume(any(), any(), any()) } returns true
    }

    @Test
    fun shouldGetSelf(){
        val user = UserDTO(
            username = "u",
            bio = "b",
            email = "e",
            fullName = "n",
            avatarUrl = "a",
            birthday = null,
            createdAt = Date(System.currentTimeMillis()),
            rooms = listOf(),
        )

        every { userService.getSelf() } returns user

        mockMvc.get("/api/user") {
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.username", "u") }
            .andExpect { jsonPath("$.bio", "b") }
            .andExpect { jsonPath("$.email", "e") }
    }

    @Test
    fun shouldUpdateUser(){
        every { userService.editProfile(any()) } returns Unit

        mockMvc.put("/api/user/edit") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "bio":"b",
                    "email":"e",
                    "fullName":"f",
                    "avatarUrl":"a"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("User updated successfully") } }
    }

    @Test
    fun shouldUpdatePassword(){
        every { userService.editPassword(any()) } returns Unit

        mockMvc.patch("/api/user/edit/password") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "oldPassword":"old p",
                    "newPassword":"new p"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Password changed successfully") } }
    }

    @Test
    fun shouldDeleteUser(){
        every { userService.deleteUser() } returns Unit

        mockMvc.delete("/api/user/delete")
            .andExpect { status { isOk() } }
            .andExpect { content { string("User deleted successfully") } }
    }

    // ==========================
    // HTTP error mapping
    // ==========================
    @Test
    fun shouldReturnABadRequest(){
        every { userService.editPassword(any()) } throws WrongPasswordException()

        mockMvc.patch("/api/user/edit/password") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "oldPassword":"old p",
                    "newPassword":"new p"
                }
            """.trimIndent()
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { content { string("Wrong password") } }
    }

    @Test
    fun shouldReturnUnauthorized(){
        every { userService.editPassword(any()) } throws InvalidTokenException()

        mockMvc.patch("/api/user/edit/password") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "oldPassword": "old p",
                    "newPassword": "new p"
                }
            """.trimIndent()
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { content { string("Invalid token") } }
    }
}