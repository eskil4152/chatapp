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
        @CookieValue("AUTH") authCookie: String?
    ) : ResponseEntity<UserDTO>? {
        if (authCookie == null) return ResponseEntity.status(401).body(null)

        val user = userService.getSelf(authCookie)

        return ResponseEntity.ok(user)
    }

    @PutMapping("/edit")
    fun updateInfo(
        @RequestBody changeUserDTO: ChangeUserDTO,
        @CookieValue("AUTH") authCookie: String?
    ) : ResponseEntity<String> {
        if (authCookie == null) return ResponseEntity.status(401).body("No cookie found")

        userService.editProfile(changeUserDTO, authCookie)

        return ResponseEntity.ok("Updated successfully")
    }

    @PatchMapping("/edit/password")
    fun changePassword(
        @RequestBody passwords: EditPasswordDTO,
        @CookieValue("AUTH") authCookie: String?
    ) : ResponseEntity<String> {
        if (authCookie == null) return ResponseEntity.status(401).body("No cookie found")

        userService.editPassword(authCookie, passwords)

        return ResponseEntity.ok().body("Password changed successfully")
    }
}