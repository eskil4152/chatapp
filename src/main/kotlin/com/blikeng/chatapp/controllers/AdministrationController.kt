package com.blikeng.chatapp.controllers;

import com.blikeng.chatapp.services.AdministrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
class AdministrationController(
    private val administrationService: AdministrationService
) {
    @GetMapping("/users")
    fun getElevatedUsers(): ResponseEntity<String> {
        return ResponseEntity.ok("Elevated users")
    }

    @PostMapping("/change-user-role")
    fun changeUserRole(): ResponseEntity<String> {
        return ResponseEntity.ok("Banned user")
    }

    @GetMapping("/user/{userId}")
    fun getUser(
        @PathVariable userId: String
    ) : ResponseEntity<String> {
        return ResponseEntity.ok("User: $userId")
    }

    @PostMapping("/ban-user")
    fun banUser(): ResponseEntity<String>{
        return ResponseEntity.ok("Banned user")
    }

    @PostMapping("/unban-user")
    fun unbanUser(): ResponseEntity<String> {
        return ResponseEntity.ok("Unbanned user")
    }
}
