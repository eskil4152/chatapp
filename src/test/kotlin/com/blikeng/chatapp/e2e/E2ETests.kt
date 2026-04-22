package com.blikeng.chatapp.e2e

import com.blikeng.chatapp.errors.ErrorMessages
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.Cookie
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2ETests : ContainerBase() {
    // ==========================
    // E2E tests covering the full expected lifecycle of a user session. Tests cover:
    // - Failed login and registration attempts
    // - Successful registration and access to protected endpoints
    // - Room listing and room creation
    // - User profile retrieval and updates
    // - Multi-user interactions
    // - Room access, messaging, and persistence
    // - Room changes and DELETE endpoints
    // - Friend management
    // ==========================

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @LocalServerPort
    private var port: Int = 0

    var user1Cookie: Cookie? = null
    var user2Cookie: Cookie? = null
    var user1Id: String? = null
    var user2Id: String? = null
    var roomId: String? = null
    var encryptedRoomId: String? = null
    var openInviteId: String? = null

    @BeforeAll
    fun setup(@Autowired jdbcTemplate: JdbcTemplate) {
        jdbcTemplate.execute("TRUNCATE TABLE user_rooms, chats, rooms, users, friends, invites CASCADE")
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
        val result = mockMvc.get("/api/user"){
            user1Cookie?.let { cookie(it) }
        }
            .andExpect {
                jsonPath("$.username") {
                    value("username")
                }
            }
            .andExpect { status { isOk() } }
            .andReturn()

        user1Id = objectMapper.readTree(result.response.contentAsString)["userId"].asText()
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
                jsonPath("$[0].roomName") { value("Brand new, nonencrypted room") }
            }
            .andReturn()

        val json = objectMapper
            .readTree(result.response.contentAsString)

        roomId = json[0]["roomId"].asText()
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

        val json = objectMapper
            .readTree(result.response.contentAsString)

        assertEquals("I am a test user", json["bio"].asText())
        assertEquals("e@mail.com", json["email"].asText())
        assertEquals("Full Name", json["fullName"].asText())
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
            .andExpect { content { ErrorMessages.WRONG_PASSWORD } }
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
            .andExpect { content { ErrorMessages.SHORT_PASSWORD } }
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
        val roomsResult = mockMvc.get("/api/rooms"){
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        assertEquals("[]", roomsResult.response.contentAsString)

        val userResult = mockMvc.get("/api/user") {
            user2Cookie?.let { cookie(it) }
        }.andReturn()
        user2Id = objectMapper.readTree(userResult.response.contentAsString)["userId"].asText()
    }

    @Test
    @Order(23)
    fun shouldInviteAndJoinRoom(){
        mockMvc.post("/api/invites/room") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"ROOM_INVITE","targetUsername":"username2","roomId":"$roomId","expiresAt":${System.currentTimeMillis() + 604800000}}"""
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }

        val pendingResult = mockMvc.get("/api/invites/pending") {
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        val inviteId = objectMapper.readTree(pendingResult.response.contentAsString)[0]["id"].asText()

        mockMvc.post("/api/invites/respond") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteId":"$inviteId","response":"ACCEPTED"}"""
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
                jsonPath("$[0].roomName") { value("Brand new, nonencrypted room") }
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
        val latch = CountDownLatch(1)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                if (message.payload.contains("ROOM_JOINED")) {
                    latch.countDown()
                }
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

        assertTrue(received.any { it.contains("ROOM_JOINED") })

        session.close()
    }

    @Test
    @Order(27)
    fun shouldEnterRoom() {
        val received = CopyOnWriteArrayList<String>()
        val joinedLatch = CountDownLatch(1)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload

                if (message.payload.contains("ROOM_JOINED")) {
                    joinedLatch.countDown()
                }
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

        assertTrue(joinedLatch.await(5, TimeUnit.SECONDS), "Did not receive ROOM_JOINED. Received: $received")
        assertTrue(received.any { it.contains("ROOM_JOINED") }, "Received: $received")

        session.close()
    }

    @Test
    @Order(28)
    fun shouldGetRoomHistory() {
        mockMvc.get("/api/chats/$roomId?page=0&size=25") {
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                content {
                    string(containsString("hello from user1"))
                }
            }
    }

    @Test
    @Order(29)
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

        val json = objectMapper
            .readTree(result.response.contentAsString)

        encryptedRoomId = json
            .first { it["encrypted"].asBoolean() }["roomId"].asText()
    }

    @Test
    @Order(30)
    fun shouldInviteAndJoinEncryptedRoom(){
        mockMvc.post("/api/invites/room") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"ROOM_INVITE","targetUsername":"username","roomId":"$encryptedRoomId"}"""
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }

        val pendingResult = mockMvc.get("/api/invites/pending") {
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        val inviteId = objectMapper.readTree(pendingResult.response.contentAsString)[0]["id"].asText()

        mockMvc.post("/api/invites/respond") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteId":"$inviteId","response":"ACCEPTED"}"""
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(31)
    fun shouldEnterEncryptedRoomAndSendMessage() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                if (message.payload.contains("ROOM_JOINED")) {
                    latch.countDown()
                }
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

        assertTrue(received.any { it.contains("ROOM_JOINED") })

        session.close()
    }

    @Test
    @Order(32)
    fun shouldEnterEncryptedRoom() {
        val received = CopyOnWriteArrayList<String>()
        val joinedLatch = CountDownLatch(1)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload

                if (message.payload.contains("ROOM_JOINED")) {
                    joinedLatch.countDown()
                }
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

        assertTrue(joinedLatch.await(5, TimeUnit.SECONDS), "Did not receive ROOM_JOINED. Received: $received")
        assertTrue(received.any { it.contains("ROOM_JOINED") }, "Received: $received")

        session.close()
    }

    @Test
    @Order(33)
    fun shouldGetEncryptedRoomHistory() {
        mockMvc.get("/api/chats/$encryptedRoomId?page=0&size=25") {
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                jsonPath("$[0].message") { value("username joined the room!") }
                jsonPath("$[1].message") { value("encrypted hello from user1") }
            }
    }

    @Test
    @Order(34)
    fun shouldEditRoomName(){
        mockMvc.put("/api/rooms/edit") {
            contentType = MediaType.APPLICATION_JSON
            content = """
            {
                "roomName":"New Named Encrypted room",
                "roomId":"$encryptedRoomId"
            }
        """.trimIndent()
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/rooms") {
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                jsonPath("$[?(@.roomId == '$encryptedRoomId')].roomName")
                    .value("New Named Encrypted room")
            }
    }

    @Test
    @Order(35)
    fun shouldFailToSendFriendRequestToNonExistingUser(){
        mockMvc.post("/api/invites/friend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"notarealuser"}"""
            user2Cookie?.let { cookie(it) }
        }.andExpect { status { isNotFound() } }
    }

    @Test
    @Order(36)
    fun shouldSendAndAcceptFriendRequest(){
        mockMvc.post("/api/invites/friend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"username"}"""
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }

        val pendingResult = mockMvc.get("/api/invites/pending") {
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        val inviteId = objectMapper.readTree(pendingResult.response.contentAsString)[0]["id"].asText()

        mockMvc.post("/api/invites/respond") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteId":"$inviteId","response":"ACCEPTED"}"""
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/friends") {
            user2Cookie?.let { cookie(it) }
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].username") { value("username") }
        }
    }

    @Test
    @Order(37)
    fun shouldMakePrivateRoom(){
        mockMvc.post("/api/rooms/dm") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"$user1Id"}"""
            user2Cookie?.let { cookie(it) }
        }.andExpect { status { isCreated() } }
    }

    @Test
    @Order(38)
    fun shouldLeaveRoom(){
        mockMvc.delete("/api/rooms/leave") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$encryptedRoomId"
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Left room successfully")} }

        mockMvc.get("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId"
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                content {
                    string(not(containsString(encryptedRoomId)))
                }
            }
    }

    @Test
    @Order(39)
    fun shouldDeleteRoom(){
        mockMvc.delete("/api/rooms/delete") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId"
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Deleted room successfully")} }

        mockMvc.get("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId"
                }
            """.trimIndent()
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect {
                content {
                    string(not(containsString(roomId)))
                }
            }
    }

    @Test
    @Order(40)
    fun shouldReceiveFriendSnapshotOnSync() {
        val received = CopyOnWriteArrayList<String>()
        val latch = CountDownLatch(1)

        val handler = object : TextWebSocketHandler() {
            override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
                received += message.payload
                if (message.payload.contains("FRIEND_SNAPSHOT")) latch.countDown()
            }
        }

        val headers = WebSocketHttpHeaders().apply {
            add(HttpHeaders.COOKIE, "AUTH=${user1Cookie!!.value}")
        }

        val session = StandardWebSocketClient()
            .execute(handler, headers, URI("ws://localhost:$port/ws"))
            .get(5, TimeUnit.SECONDS)

        session.sendMessage(TextMessage("""{"type":"SYNC"}"""))

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Did not receive FRIEND_SNAPSHOT. Got: $received")
        assertTrue(received.any { it.contains("FRIEND_SNAPSHOT") })

        session.close()
    }

    @Test
    @Order(41)
    fun shouldCreateOpenRoomInvite() {
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"roomName":"Open invite room","encrypted":false}"""
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isCreated() } }

        val roomsResult = mockMvc.get("/api/rooms") {
            user1Cookie?.let { cookie(it) }
        }.andReturn()

        val openInviteRoomId = objectMapper.readTree(roomsResult.response.contentAsString)
            .first { it["roomName"].asText() == "Open invite room" }["roomId"].asText()

        val inviteResult = mockMvc.post("/api/invites/open") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"type":"OPEN_ROOM_INVITE","roomId":"$openInviteRoomId","maxUsages":5}"""
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andReturn()

        openInviteId = inviteResult.response.contentAsString.trim('"')
    }

    @Test
    @Order(42)
    fun shouldJoinRoomViaOpenInvite() {
        mockMvc.post("/api/invites/respond") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"inviteId":"$openInviteId","response":"ACCEPTED"}"""
            user2Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
    }

    @Test
    @Order(43)
    fun shouldLogOut(){
        val result = mockMvc.post("/api/logout") {
            contentType = MediaType.APPLICATION_JSON
            user1Cookie?.let { cookie(it) }
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("User logged out") } }
            .andReturn()

        val authCookie = result.response.cookies.find { it.name == "AUTH" }
        assertNotNull(authCookie)
        assertEquals("", authCookie.value)
        assertEquals(0, authCookie.maxAge)

        user1Cookie = authCookie
    }

}