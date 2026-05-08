package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.user.ChangeUserDTO
import com.blikeng.chatapp.dtos.user.EditPasswordDTO
import com.blikeng.chatapp.dtos.user.UserDTO
import com.blikeng.chatapp.services.UserService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// ==========================
// Exposes user profile endpoints for retrieving the authenticated user,
// updating profile information, and changing the account password.
// ==========================
@RestController
@RequestMapping("/api/user")
class UserController(
    private val userService: UserService,
) {
    @GetMapping
    fun getInfo(): ResponseEntity<UserDTO>? {
        val user = userService.getSelf()
        return ResponseEntity.ok(user)
    }

    @PutMapping("/edit")
    fun updateInfo(
        @RequestBody changeUserDTO: ChangeUserDTO,
    ): ResponseEntity<String> {
        userService.editProfile(changeUserDTO)

        return ResponseEntity.ok("User updated successfully")
    }

    @PatchMapping("/edit/password")
    fun changePassword(
        @RequestBody passwords: EditPasswordDTO,
    ): ResponseEntity<String> {
        userService.editPassword(passwords)

        return ResponseEntity.ok("Password changed successfully")
    }

    @DeleteMapping("/delete")
    fun deleteUser(): ResponseEntity<String> {
        userService.deleteUser()

        return ResponseEntity.ok("User deleted successfully")
    }
}
