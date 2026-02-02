package com.blikeng.chatapp.controllerTests

import com.blikeng.chatapp.controllers.RoomController
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.services.RoomService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import jakarta.servlet.http.Cookie
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.*
import kotlin.test.Test

@WebMvcTest(RoomController::class)
class RoomControllerTests {
    @MockkBean private lateinit var roomService: RoomService
    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun shouldGetAllRooms(){
        val room = RoomEntity(id = UUID.randomUUID(), name = "r")
        every { roomService.getAllUserRooms("token") } returns listOf(room)

        val rooms = mockMvc.get("/api/rooms") {
            cookie(Cookie("AUTH", "token"))
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assert(rooms.contains(room.name))
    }

    @Test
    fun shouldFailToGetRoomsWhenNoCookie(){
        mockMvc.get("/api/rooms") {
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun shouldMakeNewRoom(){
        every { roomService.makeNewRoom("room", "token") } returns Unit

        mockMvc.post("/api/rooms/make") {
            cookie(Cookie("AUTH", "token"))
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"roomName\":\"room\"\n" +
                    "}"
        }
            .andExpect { status { isCreated() } }
            .andExpect { content { string("Room created successfully") } }
    }

    @Test
    fun shouldFailToMakeRoomWhenNoCookie(){
        mockMvc.post("/api/rooms/make") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"roomName\":\"room\"\n" +
                    "}"
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { content { string("No cookie found") } }
    }

    @Test
    fun shouldFailToMakeRoomWithInvalidName(){
        mockMvc.post("/api/rooms/make") {
            cookie(Cookie("AUTH", "token"))
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"roomName\":\"  \"\n" +
                    "}"
        }
            .andExpect { status { isBadRequest() } }
            .andExpect { content { string("Invalid room name") } }
    }

    @Test
    fun shouldJoinRoom(){
        val roomId = UUID.randomUUID()

        every { roomService.joinRoom(any(), "token") } returns Unit

        mockMvc.post("/api/rooms/join") {
            cookie(Cookie("AUTH", "token"))
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"roomId\":\"$roomId\"\n" +
                    "}"
        }
            .andExpect { status { isOk() } }
            .andExpect { content { string("Joined room successfully") } }
    }

    @Test
    fun shouldFailToJoinRoomWhenNoCookie(){
        val roomId = UUID.randomUUID()

        mockMvc.post("/api/rooms/join") {
            contentType = MediaType.APPLICATION_JSON
            content = "{\n" +
                    "\t\"roomId\":\"$roomId\"\n" +
                    "}"
        }
            .andExpect { status { isUnauthorized() } }
            .andExpect { content { string("No cookie found") } }
    }
}
