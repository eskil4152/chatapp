package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.DTOs.ChangeUserDTO
import com.blikeng.chatapp.DTOs.UserDTO
import com.blikeng.chatapp.services.UserService
import org.apache.coyote.Response
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user")
class UserController(@Autowired private val userService: UserService) {

    @GetMapping("/")
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
}