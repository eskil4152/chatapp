package com.blikeng.chatapp.securityTests

import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {
    // ==========================
    // Security integration tests.
    // Verifies that all protected endpoints return 401 when no cookie or invalid cookie is present.
    // ==========================
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun shouldReturn401WhenNoCookieGet() {
        mockMvc.get("/api/user")
            .andExpect { status { isUnauthorized() } }
            .andExpect { status { reason("Invalid token") } }
    }

    @Test
    fun shouldReturn401WhenCookieInvalidGet() {
        mockMvc.get("/api/user") {
            cookie(Cookie("AUTH", "bleh"))
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { status { reason("Invalid token") } }
    }

    @Test
    fun shouldReturn401WhenNoCookiePost(){
        mockMvc.post("/api/rooms/make"){
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                "\t\"roomName\":\"room\",\n" +
                        "\t\"encrypted\":false\n" +
                        "}"
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { status { reason("Invalid token") } }
    }

    @Test
    fun shouldReturn401WhenInvalidCookiePost(){
        mockMvc.post("/api/rooms/make"){
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"roomName\":\"room\",\n" +
                    "\t\"encrypted\":false\n" +
                    "}"
            cookie(Cookie("AUTH", "bleh"))
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { status { reason("Invalid token") } }
    }
}