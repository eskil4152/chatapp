package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.friends.FriendDTO
import com.blikeng.chatapp.dtos.UsernameDTO
import com.blikeng.chatapp.services.FriendsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// ==========================
// Exposes friend management endpoints for retrieving friends,
// adding and removing friends, and fetching friend profile information.
// ==========================
@RestController
@RequestMapping("/api/friends")
class FriendsController(@Autowired private val friendsService: FriendsService) {
    @GetMapping
    fun getFriends(): ResponseEntity<List<FriendDTO>> {
        val friends = friendsService.getFriends();

        return ResponseEntity.ok(friends)
    }

    @PostMapping("/add")
    fun addFriend(
        @RequestBody usernameDTO: UsernameDTO
    ) : ResponseEntity<String> {
        friendsService.addFriend(usernameDTO.username)

        return ResponseEntity.ok("Added friend successfully")
    }

    @DeleteMapping("/remove")
    fun removeFriend(
        @RequestBody usernameDTO: UsernameDTO
    ) : ResponseEntity<String> {
        friendsService.removeFriend(usernameDTO.username)

        return ResponseEntity.ok("Removed friend successfully")
    }

    @GetMapping("/{username}")
    fun getFriendsInfo(
        @PathVariable username: String
    ) : ResponseEntity<FriendDTO> {
        val friend = friendsService.getFriendInfo(username)

        return ResponseEntity.ok(friend)
    }
}