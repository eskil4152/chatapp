package com.blikeng.chatapp.controllers

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

@RestController
@RequestMapping("/api/rooms")
class RoomController(@Autowired private val chatService: ChatService) {

    @Autowired
    private lateinit var roomService: RoomService

    @Autowired
    private lateinit var jwtService: JwtService

    @GetMapping("/get")
    fun getRooms(){
        return
    }

    @PostMapping("/make")
    fun makeRoom(
        @CookieValue("AUTH") authCookie: String?,
        @RequestBody roomName: String): ResponseEntity<String>
    {
        if (authCookie == null) return ResponseEntity.badRequest().body("No cookie found")

        val userId = jwtService.validateToken(authCookie) ?: return ResponseEntity.badRequest().body("Invalid cookie")

        val room = roomService.makeNewRoom(roomName, userId)

        if (room == null) return ResponseEntity.badRequest().body("Failed to create room")

        return ResponseEntity.ok("Room created successfully")
    }

    @DeleteMapping("/leave")
    fun leaveRooms(){

    }

    @PostMapping("/join")
    fun joinRoom(){

    }
}