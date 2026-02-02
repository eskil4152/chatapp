package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.RoomInfo
import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.services.RoomService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/rooms")
class RoomController(
    @Autowired private val roomService: RoomService,
) {
    @GetMapping
    fun getRooms(
        @CookieValue("AUTH") authCookie: String?
    ): ResponseEntity<List<RoomEntity>> {
        if (authCookie == null) return ResponseEntity.status(401).body(emptyList())

        val rooms = roomService.getAllUserRooms(authCookie)

        return ResponseEntity.ok(rooms)
    }

    @PostMapping("/make")
    fun makeRoom(
        @CookieValue("AUTH") authCookie: String?,
        @RequestBody roomInfo: RoomInfo): ResponseEntity<String>
    {
        if (authCookie == null) return ResponseEntity.status(401).body("No cookie found")

        if (roomInfo.roomName.isNullOrBlank()) return ResponseEntity.badRequest().body("Invalid room name")
        roomService.makeNewRoom(roomInfo.roomName, authCookie)

        return ResponseEntity.status(201).body("Room created successfully")
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
}