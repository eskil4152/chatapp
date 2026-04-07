package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.UsernameDTO
import com.blikeng.chatapp.dtos.invites.FriendRequestDTO
import com.blikeng.chatapp.dtos.invites.InviteResponseDTO
import com.blikeng.chatapp.dtos.invites.OpenRoomInviteDTO
import com.blikeng.chatapp.dtos.invites.RoomInviteDTO
import com.blikeng.chatapp.services.FriendService
import com.blikeng.chatapp.services.InviteService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/invites")
class InvitesController(
    private val inviteService: InviteService
) {
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
        inviteService.createOpenRoomInvite(openRoomInviteDTO)

        return ResponseEntity.ok("Open room invite created successfully")
    }

    @PostMapping("/respond")
    fun respondToInvite(
        @RequestBody inviteResponseDTO: InviteResponseDTO
    ) : ResponseEntity<String> {
        inviteService.respondToRequest(inviteResponseDTO)

        return ResponseEntity.ok("Invite responded successfully")
    }
}