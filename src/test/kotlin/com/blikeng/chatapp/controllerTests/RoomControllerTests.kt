package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.RoomController
import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.room.RoomDTO
import com.blikeng.chatapp.dtos.room.RoomUserDTO
import com.blikeng.chatapp.entities.RoomRole
import com.blikeng.chatapp.entities.RoomType
import com.blikeng.chatapp.errors.InvalidRoomNameException
import com.blikeng.chatapp.errors.InvalidTokenException
import com.blikeng.chatapp.errors.NotPermittedException
import com.blikeng.chatapp.security.auth.JwtAuthFilter
import com.blikeng.chatapp.security.ratelimit.RateLimitService
import com.blikeng.chatapp.services.RoomService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import java.util.*
import org.junit.jupiter.api.Test

@WebMvcTest(
    controllers = [RoomController::class],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [JwtAuthFilter::class]
        )
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class RoomControllerTests {
    // ==========================
    // Tests for RoomController. Verifies:
    // - Retrieving user rooms
    // - Creating rooms
    // - Joining rooms
    // - Updating room names
    // - Deleting rooms
    // - Creating private message rooms
    // - HTTP error mapping for service exceptions
    // ==========================

    @MockkBean private lateinit var roomService: RoomService
    @Autowired private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var rateLimitService: RateLimitService

    @BeforeEach
    fun setup() {
        every { rateLimitService.tryConsume(any(), any(), any()) } returns true
    }

    @Test
    fun shouldGetAllRooms(){
        val room = RoomDTO(roomId = UUID.randomUUID().toString(), encrypted = false, roomName = "r", role = RoomRole.OWNER, type = RoomType.GROUP)
        every { roomService.getAllUserRooms() } returns listOf(room)

        val rooms = mockMvc.get("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assert(rooms.contains(room.roomName.toString()))
    }

    @Test
    fun shouldMakeNewRoom(){
        every { roomService.makeNewRoom("room", false) } returns Unit

        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomName":"room",
                    "encrypted":false
                }
            """.trimIndent()
        }
            .andExpect { status { isCreated() } }
            .andExpect { content { string("Room created successfully") } }
    }

    @Test
    fun shouldUpdateRoomName(){
        val roomId = UUID.randomUUID()

        every { roomService.editRoom(any()) } returns Unit

        mockMvc.put("/api/rooms/edit") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId",
                    "roomName":"new name"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
    }

    @Test
    fun shouldDeleteRoom(){
        val roomId = UUID.randomUUID()

        every { roomService.deleteRoom(any()) } returns Unit

        mockMvc.delete("/api/rooms/delete") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
    }

    @Test
    fun shouldCreatePrivateRoom(){
        val friendId = UUID.randomUUID()
        val userIdDTO = UserIdDTO(userId = friendId.toString())
        every { roomService.getOrStartPrivateMessage(userIdDTO) } returns UUID.randomUUID()

        mockMvc.post("/api/rooms/dm"){
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"$friendId"}"""
        }
            .andExpect { status { isCreated() } }
    }

    @Test
    fun shouldKickOrBanUser(){
        val roomId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        every { roomService.removeUserFromRoom(any()) } returns Unit

        mockMvc.post("/api/rooms/action") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId",
                    "userId":"$targetId",
                    "action":"KICK",
                    "reason":"because"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Removed user successfully") } }
    }

    // ==========================
    // HTTP error mapping
    // ==========================
    @Test
    fun shouldGetUnauthorized(){
        every { roomService.makeNewRoom(any(), any()) } throws InvalidTokenException()

        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomName":"room",
                    "encrypted":false
                }
            """.trimIndent()
        }.andExpect {
            status { isUnauthorized() }
            content { content().string("Invalid token") }
        }
    }

    @Test
    fun shouldGetBadRequest(){
        every { roomService.makeNewRoom(any(), any()) } throws InvalidRoomNameException()

        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomName":"",
                    "encrypted":false
                }
            """.trimIndent()
        }.andExpect {
            status { isBadRequest() }
            content { content { string("Invalid room name") }}
        }
    }

    @Test
    fun shouldGetForbiddenWhenNotPermittedToKickOrBanUser(){
        val roomId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        every { roomService.removeUserFromRoom(any()) } throws NotPermittedException()

        mockMvc.post("/api/rooms/action") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId",
                    "userId":"$targetId",
                    "action":"KICK",
                    "reason":""
                }
            """.trimIndent()
        }
            .andExpect { status { isForbidden() } }
            .andExpect { content { string("Not permitted") } }
    }

    @Test
    fun shouldUnbanUser() {
        val roomId = UUID.randomUUID()
        val targetId = UUID.randomUUID()

        every { roomService.unbanUser(any()) } returns Unit

        mockMvc.delete("/api/rooms/unban") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"roomId":"$roomId","userId":"$targetId"}"""
        }
            .andExpect { status { isOk() } }
    }

    @Test
    fun shouldGetBannedUsers() {
        val roomId = UUID.randomUUID()

        every { roomService.getAllBansForRoom(any()) } returns emptyList()

        mockMvc.get("/api/rooms/$roomId/bans") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"roomId":"$roomId"}"""
        }
            .andExpect { status { isOk() } }
    }

    @Test
    fun shouldGetRoomMembers() {
        val roomId = UUID.randomUUID()
        val member = RoomUserDTO(id = UUID.randomUUID(), username = "alice", avatarUrl = null, online = true, role = RoomRole.MEMBER)

        every { roomService.getAllUsersInRoom(roomId.toString()) } returns listOf(member)

        mockMvc.get("/api/rooms/$roomId/members")
            .andExpect { status { isOk() } }
            .andExpect { jsonPath("$[0].username") { value("alice") } }
            .andExpect { jsonPath("$[0].role") { value("MEMBER") } }
    }

    @Test
    fun shouldChangeRole() {
        every { roomService.changeRole(any()) } returns Unit

        mockMvc.post("/api/rooms/changeRole") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"${UUID.randomUUID()}","roomId":"${UUID.randomUUID()}","action":"PROMOTE"}"""
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Role updated successfully") } }
    }
}
