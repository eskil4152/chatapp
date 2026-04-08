package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.room.*
import com.blikeng.chatapp.services.RoomService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// ==========================
// Exposes room management endpoints for listing rooms, creating rooms,
// leaving and deleting rooms, editing rooms, banning/unbanning members,
// administrative actions, and starting private message rooms.
// Joining is handled via the invite flow (InvitesController).
// ==========================
@RestController
@RequestMapping("/api/rooms")
class RoomController(
    private val roomService: RoomService,
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
        @RequestBody userIdDTO: UserIdDTO
    ) : ResponseEntity<String> {
        return ResponseEntity.status(201).body(roomService.getOrStartPrivateMessage(userIdDTO).toString())
    }

    @PostMapping("/changeRole")
    fun changeRole(
        @RequestBody changeRoleDTO: ChangeRoleDTO
    ) : ResponseEntity<String> {
        roomService.changeRole(changeRoleDTO)

        return ResponseEntity.ok("Role updated successfully")
    }

    @PostMapping("/action")
    fun kickOrBanUser(
        @RequestBody administrationDTO: AdministrationDTO
    ) : ResponseEntity<String> {
        roomService.removeUserFromRoom(administrationDTO)

        return ResponseEntity.ok("Removed user successfully")
    }

    @DeleteMapping("/unban")
    fun unbanUser(
        @RequestBody unbanDTO: UnbanDTO
    ) : ResponseEntity<String> {
        roomService.unbanUser(unbanDTO)

        return ResponseEntity.ok("Unbanned user successfully")
    }

    @GetMapping("{roomId}/bans")
    fun getBans(
        @PathVariable roomId: String
    ) : ResponseEntity<List<RoomUserDTO>> {
        val bannedUsers = roomService.getAllBansForRoom(roomId)

        return ResponseEntity.ok(bannedUsers)
    }

    @GetMapping("/{roomId}/members")
    fun getRoomMember(
        @PathVariable roomId: String
    ) : ResponseEntity<List<RoomUserDTO>> {
        return ResponseEntity.ok(roomService.getAllUsersInRoom(roomId))
    }
}