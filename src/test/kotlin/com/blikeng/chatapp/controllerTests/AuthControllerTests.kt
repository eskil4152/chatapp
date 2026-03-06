package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.AuthController
import com.blikeng.chatapp.security.JwtAuthFilter
import com.blikeng.chatapp.services.AuthService
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test

@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTests {
    @MockkBean private lateinit var authService: AuthService
    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun shouldRegisterUserAndSetCookie() {
        every { authService.registerUser("u", "p") } returns "token"

        mockMvc.post("/api/register") {
                contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"username\":\"u\",\n" +
                    "\t\"password\":\"p\"\n" +
                    "}"
            }
            .andExpect { status { isCreated()} }
            .andExpect { content().string("User registered successfully") }
            .andExpect { cookie().exists("AUTH") }
    }

    @Test
    fun shouldLoginUserAndSetCookie() {
        every { authService.loginUser("u", "p") } returns "token"

        mockMvc.post("/api/login") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"username\":\"u\",\n" +
                    "\t\"password\":\"p\"\n" +
                    "}"
        }
            .andExpect { status { isOk()} }
            .andExpect { content().string("User logged in") }
            .andExpect { cookie().exists("AUTH") }
    }

    @Test
    fun shouldFailLoginWithWrongCredentials() {
        every { authService.loginUser("u", "p") } throws ResponseStatusException(HttpStatus.UNAUTHORIZED)

        mockMvc.post("/api/login") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"username\":\"u\",\n" +
                    "\t\"password\":\"p\"\n" +
                    "}"
        }
            .andExpect { status { isUnauthorized()} }
            .andExpect { content().string("Invalid credentials") }
    }

    @Test
    fun shouldGetConflict() {
        every { authService.registerUser("u", "p") } throws ResponseStatusException(HttpStatus.CONFLICT)

        mockMvc.post("/api/register") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"username\":\"u\",\n" +
                    "\t\"password\":\"p\"\n" +
                    "}"
        }
            .andExpect { status { isConflict() } }
            .andExpect { content().string("Username already exists") }
    }

    @Test
    fun shouldReturnOkForAuthEndpoint() {
        mockMvc.get("/api/auth") {
        }
            .andExpect { status { isOk() } }
    }
}
