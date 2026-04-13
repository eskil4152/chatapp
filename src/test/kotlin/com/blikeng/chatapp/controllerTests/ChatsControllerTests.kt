package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.ChatsController
import com.blikeng.chatapp.dtos.messaging.SendMessageDTO
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import com.blikeng.chatapp.services.ChatService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import java.util.*
import kotlin.test.Test

@WebMvcTest(
    controllers = [ChatsController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class ChatsControllerTests {
    // ==========================
    // Tests for ChatsController. Verifies:
    // - Message retrieval from service
    // - UUID
    // ==========================

    @MockkBean private lateinit var chatService: ChatService
    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setup() {
        every { rateLimitService.tryConsume(any(), any(), any()) } returns true
    }

    @Test
    fun shouldGetMessages() {
        val roomId = UUID.randomUUID()

        val message = SendMessageDTO(
            id = UUID.randomUUID(),
            roomId = roomId,
            userId = UUID.randomUUID(),
            username = "user",
            message = "message",
            timestamp = Instant.now(),
        )

        every { chatService.getRoomMessages(any(), any(), any()) } returns listOf(message)

        mockMvc.get("/api/chats/${roomId}?page=0&size=25") {
            contentType = MediaType.APPLICATION_JSON
        }
            .andExpect { status { isOk()} }
            .andExpect { content { string(containsString("message")) } }
    }

    @Test
    fun shouldThrowInvalidUUIDException() {
        mockMvc.get("/api/chats/123?page=0&size=25") {
            contentType = MediaType.APPLICATION_JSON
        }
            .andExpect { status { isBadRequest()} }
    }
}
