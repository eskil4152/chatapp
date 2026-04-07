package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.UsernameDTO
import com.blikeng.chatapp.dtos.friends.FriendDTO
import com.blikeng.chatapp.services.FriendService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// ==========================
// Exposes friend management endpoints for listing friends,
// removing friends, and fetching friend profile information.
// Adding friends is handled via the invite flow (InvitesController).
// ==========================
@RestController
@RequestMapping("/api/friends")
class FriendsController(private val friendService: FriendService) {
    @GetMapping
    fun getFriends(): ResponseEntity<List<FriendDTO>> {
        val friends = friendService.getFriends()

        return ResponseEntity.ok(friends)
    }

    @DeleteMapping("/remove")
    fun removeFriend(
        @RequestBody usernameDTO: UsernameDTO
    ) : ResponseEntity<String> {
        friendService.removeFriend(usernameDTO.username)

        return ResponseEntity.ok("Removed friend successfully")
    }

    @GetMapping("/{username}")
    fun getFriendsInfo(
        @PathVariable username: String
    ) : ResponseEntity<FriendDTO> {
        val friend = friendService.getFriendInfo(username)

        return ResponseEntity.ok(friend)
    }
}