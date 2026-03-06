package com.blikeng.chatapp.e2e

import com.blikeng.chatapp.ErrorMessages.INVALID_PASSWORD
import com.blikeng.chatapp.ErrorMessages.SHORT_PASSWORD
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.URI
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2ETests : PostgresContainerBase() {
    @Autowired private lateinit var mockMvc: MockMvc
    @LocalServerPort
    private var port: Int = 0

    var user1Cookie: Cookie? = null
    var user2Cookie: Cookie? = null
    var roomId: String? = null
    var encryptedRoomId: String? = null

    @BeforeAll
    fun setup(@Autowired jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.execute("DELETE FROM user_rooms")
        jdbcTemplate.execute("DELETE FROM chats")
        jdbcTemplate.execute("DELETE FROM rooms")
        jdbcTemplate.execute("DELETE FROM users")
    }

    @Test
    @Order(1)
    fun shouldFailToAccessAuthWithoutCookie(){
        mockMvc.get("/api/auth")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @Order(2)
    fun shouldFailToAccessUserWithoutCookie(){
        mockMvc.get("/api/user")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @Order(3)
    fun shouldFailToLogInWithNonExistingUser(){
        mockMvc.post("/api/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"user",
                    "password":"pass"
                }
            """.trimIndent()
        }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @Order(4)
    fun shouldFailToRegisterUserWithEmptyUsernameOrEmptyPassword(){
        mockMvc.post("/api/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"",
                    "password":"password"
                }
            """.trimIndent()
        }
            .andExpect { status { isBadRequest() } }

        mockMvc.post("/api/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"username",
                    "password":""
                }
            """.trimIndent()
        }
            .andExpect { status { isBadRequest() } }
    }

    @Test
    @Order(5)
    fun shouldRegisterUserWithValidData(){
        val result = mockMvc.post("/api/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"username",
                    "password":"password"
                }
            """.trimIndent()
        }
            .andExpect { status { isCreated() } }
            .andExpect { content { string("User registered successfully") } }
            .andReturn()

        user1Cookie = result.response.cookies.find { it.name == "AUTH" }
    }

    @Test
    @Order(6)
    fun shouldFailRegisteringExistingUser(){
        mockMvc.post("/api/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"username",
                    "password":"password"
                }
            """.trimIndent()
        }
            .andExpect { status { isConflict() } }
    }

    @Test
    @Order(7)
    fun shouldAccessAuth(){
        mockMvc.get("/api/auth") {
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(8)
    fun shouldAccessUserAndGetCorrectInfo(){
        mockMvc.get("/api/user"){
            user1Cookie?.let { cookie(it) }
        }
            .andExpect {
                jsonPath("$.username") {
                    value("username")
                }
            }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(9)
    fun shouldGetRoomsAsEmptyList(){
        val result = mockMvc.get("/api/rooms"){
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        assertEquals("[]", result.response.contentAsString)
    }

    @Test
    @Order(10)
    fun shouldFailToJoinNonExistingRoom(){
        mockMvc.post("/api/rooms/join") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"${UUID.randomUUID()}"
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isNotFound()} }
    }

    @Test
    @Order(11)
    fun shouldFailToMakeRoomWithEmptyName(){
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomName":"",
                    "encrypted":false
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isBadRequest()} }
    }

    @Test
    @Order(12)
    fun shouldMakeARoom(){
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomName":"Brand new, nonencrypted room",
                    "encrypted":false
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isCreated()} }
    }

    @Test
    @Order(13)
    fun shouldGetRoomsAndGetNewlyCreatedRoom(){
        val result = mockMvc.get("/api/rooms"){
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                jsonPath("$[0].name") { value("Brand new, nonencrypted room") }
            }
            .andReturn()

        val json = jacksonObjectMapper()
            .readTree(result.response.contentAsString)

        roomId = json[0]["id"].asString()
    }

    @Test
    @Order(14)
    fun shouldEditUserAndGetUpdatedInfo(){
        mockMvc.put("/api/user/edit"){
            user1Cookie?.let { cookie(it) }
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "bio":"I am a test user",
                    "email":"e@mail.com",
                    "fullName":"Full Name",
                    "avatarUrl":""
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }

        val result = mockMvc.get("/api/user"){
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        val json = jacksonObjectMapper()
            .readTree(result.response.contentAsString)

        assertEquals("I am a test user", json["bio"].asString())
        assertEquals("e@mail.com", json["email"].asString())
        assertEquals("Full Name", json["fullName"].asString())
    }

    @Test
    @Order(15)
    fun shouldFailToEditPasswordWithWrongOldPassword(){
        mockMvc.patch("/api/user/edit/password"){
            user1Cookie?.let { cookie(it) }
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "oldPassword":"wrongPassword",
                    "newPassword":"newPassword" 
                }
            """.trimIndent()
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { status { reason(INVALID_PASSWORD) } }
    }

    @Test
    @Order(16)
    fun shouldFailToEditPasswordWithTooShortPassword(){
        mockMvc.patch("/api/user/edit/password"){
            user1Cookie?.let { cookie(it) }
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "oldPassword":"password",
                    "newPassword":"new" 
                }
            """.trimIndent()
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { status { reason(SHORT_PASSWORD) } }
    }

    @Test
    @Order(17)
    fun shouldEditPassword(){
        mockMvc.patch("/api/user/edit/password"){
            user1Cookie?.let { cookie(it) }
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "oldPassword":"password",
                    "newPassword":"newPassword" 
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andReturn()
    }

    @Test
    @Order(18)
    fun shouldFailLoginWithOldPassword(){
        mockMvc.post("/api/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"username",
                    "password":"password"
                }
            """.trimIndent()
        }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    @Order(19)
    fun shouldLogInWithNewPassword(){
        val result = mockMvc.post("/api/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"username",
                    "password":"newPassword"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andReturn()

        user1Cookie = result.response.cookies.find { it.name == "AUTH" }
    }

    @Test
    @Order(20)
    fun shouldRegisterSecondUser(){
        val result = mockMvc.post("/api/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "username":"username2",
                    "password":"password"
                }
            """.trimIndent()
        }
            .andExpect { status { isCreated() } }
            .andReturn()

        user2Cookie = result.response.cookies.find { it.name == "AUTH" }
    }

    @Test
    @Order(21)
    fun shouldAccessAuthAsSecondUser(){
        mockMvc.get("/api/auth"){
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(22)
    fun shouldGetEmptyListForSecondUserRooms(){
        val result = mockMvc.get("/api/rooms"){
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        assertEquals("[]", result.response.contentAsString)
    }

    @Test
    @Order(23)
    fun shouldJoinRoom(){
        mockMvc.post("/api/rooms/join") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId"
                }
            """.trimIndent()
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(24)
    fun shouldGetAllRoomsForSecondUser(){
        mockMvc.get("/api/rooms"){
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                jsonPath("$[0].name") { value("Brand new, nonencrypted room") }
            }
            .andReturn()
    }

    @Test
    @Order(25)
    fun shouldGetSecondUserInfo(){
        mockMvc.get("/api/user"){
            user2Cookie?.let { cookie(it) }
        }
            .andExpect {
                jsonPath("$.username") {
                    value("username2")
                }
            }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(26)
    fun shouldEnterRoomAndSendMessage() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                latch.countDown()
            }
        }

        val headers = WebSocketHttpHeaders().apply {
            add(HttpHeaders.COOKIE, "AUTH=${user1Cookie!!.value}")
        }

        val session = StandardWebSocketClient()
            .execute(handler, headers, URI("ws://localhost:$port/ws"))
            .get(5, TimeUnit.SECONDS)

        session.sendMessage(
            TextMessage(
                """
            {
                "type":"JOIN",
                "roomId":"$roomId",
                "message":""
            }
            """.trimIndent()
            )
        )

        session.sendMessage(
            TextMessage(
                """
            {
                "type":"MESSAGE",
                "roomId":"$roomId",
                "message":"hello from user1"
            }
            """.trimIndent()
            )
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertTrue(received.any { it.contains("JOINED") })

        session.close()
    }

    @Test
    @Order(27)
    fun shouldEnterRoomAndGetMessages() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                latch.countDown()
            }
        }

        val headers = WebSocketHttpHeaders().apply {
            add(HttpHeaders.COOKIE, "AUTH=${user2Cookie!!.value}")
        }

        val session = StandardWebSocketClient()
            .execute(handler, headers, URI("ws://localhost:$port/ws"))
            .get(5, TimeUnit.SECONDS)

        session.sendMessage(
            TextMessage(
                """
            {
                "type":"JOIN",
                "roomId":"$roomId",
                "message":""
            }
            """.trimIndent()
            )
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertTrue(received.any { it.contains("JOINED") })
        assertTrue(received.any { it.contains("hello from user1") })

        session.close()
    }

    @Test
    @Order(28)
    fun shouldMakeEncryptedRoom(){
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
            {
                "roomName":"Encrypted room",
                "encrypted":true
            }
        """.trimIndent()
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isCreated() } }

        val result = mockMvc.get("/api/rooms") {
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        val json = jacksonObjectMapper()
            .readTree(result.response.contentAsString)

        encryptedRoomId = json
            .first { it["encrypted"].asBoolean() }["id"].asString()
    }

    @Test
    @Order(29)
    fun shouldJoinEncryptedRoom(){
        mockMvc.post("/api/rooms/join") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$encryptedRoomId"
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(30)
    fun shouldEnterEncryptedRoomAndSendMessage() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                latch.countDown()
            }
        }

        val headers = WebSocketHttpHeaders().apply {
            add(HttpHeaders.COOKIE, "AUTH=${user1Cookie!!.value}")
        }

        val session = StandardWebSocketClient()
            .execute(handler, headers, URI("ws://localhost:$port/ws"))
            .get(5, TimeUnit.SECONDS)

        session.sendMessage(
            TextMessage(
                """
            {
                "type":"JOIN",
                "roomId":"$encryptedRoomId",
                "message":""
            }
            """.trimIndent()
            )
        )

        session.sendMessage(
            TextMessage(
                """
            {
                "type":"MESSAGE",
                "roomId":"$encryptedRoomId",
                "message":"encrypted hello from user1"
            }
            """.trimIndent()
            )
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertTrue(received.any { it.contains("JOINED") })

        session.close()
    }

    @Test
    @Order(31)
    fun shouldEnterEncryptedRoomAndGetMessages() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(2)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                latch.countDown()
            }
        }

        val headers = WebSocketHttpHeaders().apply {
            add(HttpHeaders.COOKIE, "AUTH=${user2Cookie!!.value}")
        }

        val session = StandardWebSocketClient()
            .execute(handler, headers, URI("ws://localhost:$port/ws"))
            .get(5, TimeUnit.SECONDS)

        session.sendMessage(
            TextMessage(
                """
            {
                "type":"JOIN",
                "roomId":"$encryptedRoomId",
                "message":""
            }
            """.trimIndent()
            )
        )

        assertTrue(latch.await(5, TimeUnit.SECONDS))

        assertTrue(received.any { it.contains("JOINED") })
        assertTrue(received.any { it.contains("encrypted hello from user1") })

        session.close()
    }
}