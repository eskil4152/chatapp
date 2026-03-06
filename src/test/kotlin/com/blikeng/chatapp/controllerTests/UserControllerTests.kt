package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.ErrorMessages.INVALID_PASSWORD
import com.blikeng.chatapp.ErrorMessages.INVALID_TOKEN
import com.blikeng.chatapp.controllers.UserController
import com.blikeng.chatapp.dtos.UserDTO
import com.blikeng.chatapp.security.JwtAuthFilter
import com.blikeng.chatapp.services.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.web.server.ResponseStatusException
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
    @MockkBean private lateinit var userService: UserService
    @Autowired private lateinit var mockMvc: MockMvc

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
            content = "{\n" +
                    "\t\"bio\":\"b\",\n" +
                    "\t\"email\":\"e\",\n" +
                    "\t\"fullName\":\"f\",\n" +
                    "\t\"avatarUrl\":\"a\"\n" +
                    "}"
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Updated successfully") } }
    }

    @Test
    fun shouldUpdatePassword(){
        every { userService.editPassword(any()) } returns Unit

        mockMvc.patch("/api/user/edit/password") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"oldPassword\":\"old p\",\n" +
                    "\t\"newPassword\":\"new p\"\n" +
                    "}"
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Password changed successfully") } }
    }

    @Test
    fun shouldReturnABadRequest(){
        every { userService.editPassword(any()) } throws ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_PASSWORD)

        mockMvc.patch("/api/user/edit/password") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"oldPassword\":\"old p\",\n" +
                    "\t\"newPassword\":\"new p\"\n" +
                    "}"
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { status { reason(INVALID_PASSWORD) } }
    }

    @Test
    fun shouldReturnUnauthorized(){
        every { userService.editPassword(any()) } throws ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)

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
            .andExpect { status { reason(INVALID_TOKEN) } }
    }
}