package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.UserController
import com.blikeng.chatapp.dtos.UserDTO
import com.blikeng.chatapp.services.UserService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import java.sql.Date
import kotlin.test.Test

@WebMvcTest(UserController::class)
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
            rooms = mutableSetOf(),
        )

        every { userService.getSelf("token") } returns user

        mockMvc.get("/api/user/") {
            cookie(Cookie("AUTH", "token"))
        }
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$.username", "u") }
            .andExpect { jsonPath("$.bio", "b") }
            .andExpect { jsonPath("$.email", "e") }
    }

    @Test
    fun shouldFailToGetSelfWithoutCookie(){
        mockMvc.get("/api/user/") {
        }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun shouldUpdateUser(){
        every { userService.editProfile(any(), "token") } returns Unit

        mockMvc.put("/api/user/edit") {
            cookie(Cookie("AUTH", "token"))
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
    fun shouldFailToUpdateUserWithoutCookie(){
        mockMvc.put("/api/user/edit") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"bio\":\"b\",\n" +
                    "\t\"email\":\"e\",\n" +
                    "\t\"fullName\":\"f\",\n" +
                    "\t\"avatarUrl\":\"a\"\n" +
                    "}"
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { content { string("No cookie found") } }
    }

    @Test
    fun shouldUpdatePassword(){
        every { userService.editPassword("token", any()) } returns Unit

        mockMvc.patch("/api/user/edit/password") {
            cookie(Cookie("AUTH", "token"))
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
    fun shouldFailToUpdatePasswordWithoutCookie(){
        mockMvc.patch("/api/user/edit/password") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"oldPassword\":\"old p\",\n" +
                    "\t\"newPassword\":\"new p\"\n" +
                    "}"
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { content { string("No cookie found") } }
    }
}