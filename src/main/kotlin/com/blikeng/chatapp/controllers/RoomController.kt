package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.RoomDTO
import com.blikeng.chatapp.dtos.UsernameDTO
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
        roomService.makeNewRoom(roomDTO.roomName, roomDTO.encrypted)

        return ResponseEntity.status(201).body("Room created successfully")
    }

    @PostMapping("/join")
    fun joinRoom(
        @RequestBody roomDTO: RoomDTO
    ) : ResponseEntity<String> {
        roomService.joinRoom(roomDTO.roomId)

        return ResponseEntity.ok("Joined room successfully")
    }

    @PutMapping("/edit")
    fun editRoom(
        @RequestBody roomDTO: RoomDTO
    ) : ResponseEntity<String> {
        roomService.editRoom(roomDTO)

        return ResponseEntity.ok("Room edited successfully")
    }

    @DeleteMapping("/leave")
    fun leaveRoom(
        @RequestBody roomDTO: RoomDTO
    ) : ResponseEntity<String> {
        roomService.leaveRoom(roomDTO.roomId)

        return ResponseEntity.ok("Left room successfully")
    }

    @DeleteMapping("/delete")
    fun deleteRoom(
        @RequestBody roomDTO: RoomDTO
    ) : ResponseEntity<String> {
        roomService.deleteRoom(roomDTO.roomId)

        return ResponseEntity.ok("Deleted room successfully")
    }

    @PostMapping("/dm")
    fun privateMessage(
        @RequestBody usernameDTO: UsernameDTO
    ) : ResponseEntity<String> {
        return ResponseEntity.status(201).body(roomService.getOrStartPrivateMessage(usernameDTO.username).toString());
    }
}