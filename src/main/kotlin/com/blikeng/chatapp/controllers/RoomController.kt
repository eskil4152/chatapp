package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.DTOs.RoomInfo
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.services.RoomService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/rooms")
class RoomController(
    @Autowired private val roomService: RoomService,
) {
    @GetMapping("/")
    fun getRooms(
        @CookieValue("AUTH") authCookie: String?
    ): ResponseEntity<List<RoomEntity>> {
        if (authCookie == null) return ResponseEntity.status(401).body(emptyList())

        val rooms = roomService.getAllUserRooms(authCookie)
        if (rooms == null) return ResponseEntity.status(401).body(emptyList())

        return ResponseEntity.ok(rooms)
    }

    @PostMapping("/make")
    fun makeRoom(
        @CookieValue("AUTH") authCookie: String?,
        @RequestBody roomInfo: RoomInfo): ResponseEntity<String>
    {
        if (authCookie == null) return ResponseEntity.status(401).body("No cookie found")

        if (roomInfo.roomName == null) return ResponseEntity.badRequest().body("Invalid room name")
        roomService.makeNewRoom(roomInfo.roomName, authCookie)

        return ResponseEntity.ok("Room created successfully")
    }

    @PostMapping("/join")
    fun joinRoom(
        @CookieValue("AUTH") authCookie: String?,
        @RequestBody roomInfo: RoomInfo
    ) : ResponseEntity<String> {
        if (authCookie == null) return ResponseEntity.status(401).body("No cookie found")

        val roomId = roomInfo.roomId

        roomService.joinRoom(UUID.fromString(roomId), authCookie)

        return ResponseEntity.ok("Joined room successfully")
    }

    @DeleteMapping("/leave")
    fun leaveRooms(){

    }
}