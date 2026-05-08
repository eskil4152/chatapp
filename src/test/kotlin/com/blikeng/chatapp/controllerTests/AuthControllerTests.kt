package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.AuthController
import com.blikeng.chatapp.dtos.auth.AuthDTO
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.errors.InvalidCredentialsException
import com.blikeng.chatapp.errors.UsernameAlreadyExistsException
import com.blikeng.chatapp.security.UserRole
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitingService
import com.blikeng.chatapp.services.AuthService
import com.blikeng.chatapp.services.UserService
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import java.util.UUID

@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class],
        ),
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTests {
    // ==========================
    // Tests for AuthController. Verifies:
    // - User registration
    // - User login
    // - User logout
    // - Authentication status endpoint
    // - HTTP error mapping for service exceptions
    // ==========================

    @MockkBean private lateinit var authService: AuthService

    @MockkBean private lateinit var userService: UserService

    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var rateLimitingService: RateLimitingService

    @BeforeEach
    fun setup() {
        every { rateLimitingService.tryConsume(any(), any(), any()) } returns true
    }

    // ==========================
    // Register
    // ==========================
    @Test
    fun shouldRegisterUserAndSetCookie() {
        every { authService.registerUser("username", "password") } returns "token"

        mockMvc
            .post("/api/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "username":"username",
                        "password":"password"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andExpect { content().string("User registered successfully") }
            .andExpect { cookie().exists("AUTH") }
    }

    // ==========================
    // Login
    // ==========================
    @Test
    fun shouldLoginUserAndSetCookie() {
        every { authService.loginUser("username", "password") } returns "token"

        mockMvc
            .post("/api/login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "username":"username",
                        "password":"password"
                    }
                    """.trimIndent()
            }.andExpect { status { isOk() } }
            .andExpect { content().string("User logged in") }
            .andExpect { cookie().exists("AUTH") }
    }

    @Test
    fun shouldFailLoginWithWrongCredentials() {
        every { authService.loginUser("username", "password") } throws InvalidCredentialsException()

        mockMvc
            .post("/api/login") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "username":"username",
                        "password":"password"
                    }
                    """.trimIndent()
            }.andExpect { status { isUnauthorized() } }
            .andExpect { content().string(ErrorMessages.INVALID_CREDENTIALS) }
    }

    // ==========================
    // Logout
    // ==========================
    @Test
    fun shouldLogOutUser() {
        val res =
            mockMvc
                .post("/api/logout") {
                }.andExpect { status { isOk() } }
                .andExpect { cookie().exists("AUTH") }
                .andReturn()

        assert(
            res.response.cookies.any {
                it.name == "AUTH" && it.value == "" && it.maxAge == 0
            },
        )
    }

    // ==========================
    // Auth
    // ==========================
    @Test
    fun shouldReturnOkForAuthEndpoint() {
        every { userService.authenticate() } returns AuthDTO(UUID.randomUUID(), "", UserRole.USER)

        mockMvc
            .get("/api/auth") {
            }.andExpect { status { isOk() } }
    }

    // ==========================
    // HTTP error mapping
    // ==========================
    @Test
    fun shouldGetConflict() {
        every { authService.registerUser("username", "password") } throws UsernameAlreadyExistsException()

        mockMvc
            .post("/api/register") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                        "username":"username",
                        "password":"password"
                    }
                    """.trimIndent()
            }.andExpect { status { isConflict() } }
            .andExpect { content().string(ErrorMessages.USERNAME_EXISTS) }
    }
}
