package com.blikeng.chatapp.controllers;

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDetailDTO
import com.blikeng.chatapp.services.AdministrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
class AdministrationController(
    private val administrationService: AdministrationService
) {
    @GetMapping("/users")
    fun getElevatedUsers(): ResponseEntity<List<ElevatedUserDTO>> {
        return ResponseEntity.ok(administrationService.getElevatedUsers())
    }

    @PostMapping("/change-user-role")
    fun changeUserRole(
        @RequestBody userIdDTO: UserIdDTO
    ) : ResponseEntity<ElevatedUserDetailDTO> {
        return ResponseEntity.ok(administrationService.getUser(userIdDTO))
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
