package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.services.ChatService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/rooms")
class RoomController(@Autowired private val chatService: ChatService) {

    @GetMapping("/get")
    fun getRooms(){
        return
    }

    @PostMapping("/add")
    fun addRoom(){

    }

    @DeleteMapping("/leave")
    fun leaveRooms(){

    }

    @PostMapping("/join")
    fun joinRoom(){

    }
}