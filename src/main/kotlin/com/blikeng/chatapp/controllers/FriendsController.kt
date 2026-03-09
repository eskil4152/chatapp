package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.services.FriendsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController("/api/friends")
class FriendsController(private val friendsService: FriendsService) {

    @GetMapping("")
    fun getFriends(){
        friendsService.getFriends();
    }

    @PostMapping("/add")
    fun addFriend(
        @RequestBody username: String
    ) : ResponseEntity<String> {
        friendsService.addFriend(username)

        return ResponseEntity.ok("Added friend successfully")
    }

    @DeleteMapping("/remove")
    fun removeFriend(
        @RequestBody username: String
    ) : ResponseEntity<String> {
        friendsService.removeFriend(username)

        return ResponseEntity.ok("Removed friend successfully")
    }
}