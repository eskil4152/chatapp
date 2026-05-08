package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.friends.FriendDTO
import com.blikeng.chatapp.services.FriendService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// ==========================
// Exposes friend management endpoints for listing friends,
// removing friends, and fetching friend profile information.
// Adding friends is handled via the invite flow (InvitesController).
// ==========================
@RestController
@RequestMapping("/api/friends")
class FriendsController(
    private val friendService: FriendService,
) {
    @GetMapping
    fun getFriends(): ResponseEntity<List<FriendDTO>> {
        val friends = friendService.getFriends()

        return ResponseEntity.ok(friends)
    }

    @DeleteMapping("/remove")
    fun removeFriend(
        @RequestBody userIdDTO: UserIdDTO,
    ): ResponseEntity<String> {
        friendService.removeFriend(userIdDTO)

        return ResponseEntity.ok("Removed friend successfully")
    }

    @GetMapping("/{userId}")
    fun getFriendsInfo(
        @PathVariable userId: String,
    ): ResponseEntity<FriendDTO> {
        val friend = friendService.getFriendInfo(userId)

        return ResponseEntity.ok(friend)
    }
}
