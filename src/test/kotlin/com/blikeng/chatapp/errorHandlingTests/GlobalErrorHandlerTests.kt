package com.blikeng.chatapp.errorHandlingTests

import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.GlobalExceptionHandler
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@WebMvcTest(
    controllers = [TestExceptionController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@Import(GlobalExceptionHandler::class)
class GlobalErrorHandlerTests {
    // ==========================
    // Tests for covering custom exception. Handmade controller to assert custom exceptions throws correctly
    // ==========================
    @Autowired
    lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setup() {
        every { rateLimitService.tryConsume(any(), any(), any()) } returns true
    }

    @Test
    fun shouldHandleApiException() {
        mockMvc.get("/test/api-exception")
            .andExpect {
                status { isBadRequest() }
                content { string("Custom api error") }
            }
    }

    @Test
    fun shouldHandleUnknownException() {
        mockMvc.get("/test/unknown-exception")
            .andExpect {
                status { isInternalServerError() }
                content { string("Unexpected error") }
            }
    }
}

@RestController
class TestExceptionController {

    @GetMapping("/test/api-exception")
    fun throwApiException(): String {
        throw TestApiException()
    }

    @GetMapping("/test/unknown-exception")
    fun throwUnknownException(): String {
        throw RuntimeException("boom")
    }
}

class TestApiException : ApiException(HttpStatus.BAD_REQUEST, "Custom api error")