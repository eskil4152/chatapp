package com.blikeng.chatapp.controllers

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/user/")
class UserController {

    @GetMapping("/me")
    fun getInfo(){

    }

    @PostMapping("/friend")
    fun addFriend(){

    }

    @DeleteMapping("/friend")
    fun removeFriend(){

    }

    @GetMapping("/friends")
    fun getFriends(){

    }
}