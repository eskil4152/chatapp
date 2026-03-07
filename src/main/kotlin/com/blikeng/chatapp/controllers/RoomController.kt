package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.services.RoomService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rooms")
class RoomController(
    @Autowired private val roomService: RoomService,
) {
    @GetMapping
    fun getRooms(
    ): ResponseEntity<List<RoomDTO>> {
        val rooms = roomService.getAllUserRooms()

        return ResponseEntity.ok(rooms)
    }

    @PostMapping("/make")
    fun makeRoom(
        @RequestBody roomDTO: RoomDTO): ResponseEntity<String>
    {
        if (roomDTO.roomName.isNullOrBlank()) return ResponseEntity.badRequest().body("Invalid room name")
        roomService.makeNewRoom(roomDTO.roomName, roomDTO.encrypted)

        return ResponseEntity.status(201).body("Room created successfully")
    }

    @PostMapping("/join")
    fun joinRoom(
        @RequestBody roomDTO: RoomDTO
    ) : ResponseEntity<String> {
        val roomId = roomDTO.roomId ?: return ResponseEntity.badRequest().body("Invalid room id")

        roomService.joinRoom(roomId)

        return ResponseEntity.ok("Joined room successfully")
    }

    @DeleteMapping("/leave")
    fun leaveRoom(
        @RequestBody roomDTO: RoomDTO
    ) : ResponseEntity<String> {
        val roomId = roomDTO.roomId ?: return ResponseEntity.badRequest().body("Invalid room id")

        roomService.leaveRoom(roomId)

        return ResponseEntity.ok("Left room successfully")
    }
}