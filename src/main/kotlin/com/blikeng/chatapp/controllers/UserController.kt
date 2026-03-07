package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.ChangeUserDTO
import com.blikeng.chatapp.dtos.EditPasswordDTO
import com.blikeng.chatapp.dtos.UserDTO
import com.blikeng.chatapp.services.UserService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/user")
class UserController(@Autowired private val userService: UserService) {
    @GetMapping("")
    fun getInfo(
    ) : ResponseEntity<UserDTO>? {
        val user = userService.getSelf()
        return ResponseEntity.ok(user)
    }

    @PutMapping("/edit")
    fun updateInfo(
        @RequestBody changeUserDTO: ChangeUserDTO,
    ) : ResponseEntity<String> {
        userService.editProfile(changeUserDTO)

        return ResponseEntity.ok("Updated successfully")
    }

    @PatchMapping("/edit/password")
    fun changePassword(
        @RequestBody passwords: EditPasswordDTO,
    ) : ResponseEntity<String> {
        userService.editPassword(passwords)

        return ResponseEntity.ok("Password changed successfully")
    }
}