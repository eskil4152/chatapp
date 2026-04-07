package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.invites.FriendRequestDTO
import com.blikeng.chatapp.dtos.invites.InviteResponseDTO
import com.blikeng.chatapp.dtos.invites.OpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.PendingInviteDTO
import com.blikeng.chatapp.dtos.invites.RoomInviteDTO
import com.blikeng.chatapp.services.InviteService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// ==========================
// Handles all invite flows: friend requests, room invites, open room invites,
// responding to pending invites, and listing pending invites for the current user.
// Only open room invites return an ID (used as a shareable invite code).
// ==========================
@RestController
@RequestMapping("/api/invites")
class InvitesController(
    private val inviteService: InviteService
) {
    @GetMapping("/pending")
    fun getPendingInvites(): ResponseEntity<List<PendingInviteDTO>> {
        return ResponseEntity.ok(inviteService.getPendingInvites())
    }

    @PostMapping("/friend")
    fun sendFriendRequest(
        @RequestBody friendRequestDTO: FriendRequestDTO
    ) : ResponseEntity<String> {
        inviteService.sendFriendRequest(friendRequestDTO)
        return ResponseEntity.ok("Friend request sent successfully")
    }

    @PostMapping("/room")
    fun sendRoomInvite(
        @RequestBody roomInviteDTO: RoomInviteDTO
    ) : ResponseEntity<String> {
        inviteService.sendRoomInvite(roomInviteDTO)
        return ResponseEntity.ok("Room invite sent successfully")
    }

    @PostMapping("/open")
    fun createOpenRoomInvite(
        @RequestBody openRoomInviteDTO: OpenRoomInviteDTO
    ) : ResponseEntity<String> {
        val inviteId = inviteService.createOpenRoomInvite(openRoomInviteDTO)
        return ResponseEntity.ok(inviteId.toString())
    }

    @PostMapping("/respond")
    fun respondToInvite(
        @RequestBody inviteResponseDTO: InviteResponseDTO
    ) : ResponseEntity<String> {
        inviteService.respondToRequest(inviteResponseDTO)
        return ResponseEntity.ok("Invite responded successfully")
    }
}
