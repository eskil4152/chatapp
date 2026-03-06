package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.ErrorMessages.ROOM_NOT_FOUND
import com.blikeng.chatapp.controllers.RoomController
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.security.JwtAuthFilter
import com.blikeng.chatapp.services.RoomService
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
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.Test

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
    @MockkBean private lateinit var roomService: RoomService
    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun shouldGetAllRooms(){
        val room = RoomEntity(id = UUID.randomUUID(), name = "r")
        every { roomService.getAllUserRooms() } returns listOf(room)

        val rooms = mockMvc.get("/api/rooms") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assert(rooms.contains(room.name))
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
    fun shouldFailToMakeRoomWithInvalidName(){
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { content { string("Invalid room name") } }
    }

    @Test
    fun shouldFailToMakeRoomWithBlankName(){
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomName":"",
                    "encrypted":false
                }
            """.trimIndent()
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { content { string("Invalid room name") } }
    }

    @Test
    fun shouldJoinRoom(){
        val roomId = UUID.randomUUID()

        every { roomService.joinRoom(any()) } returns Unit

        mockMvc.post("/api/rooms/join") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"$roomId"
                }
            """.trimIndent()
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Joined room successfully") } }
    }

    @Test
    fun shouldGetUnauthorized(){
        every { roomService.makeNewRoom(any(), any()) } throws ResponseStatusException(HttpStatus.UNAUTHORIZED)

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
        every { roomService.makeNewRoom(any(), any()) } throws ResponseStatusException(HttpStatus.BAD_REQUEST)

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
            content { content().string("Invalid room name") }
        }
    }

    @Test
    fun shouldGetNotFound(){
        every { roomService.joinRoom(any()) } throws ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc.post("/api/rooms/join") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {
                    "roomId":"${UUID.randomUUID()}"
                }
            """.trimIndent()
        }.andExpect {
            status { isNotFound() }
            content { content().string(ROOM_NOT_FOUND) }
        }
    }
}
