package com.blikeng.chatapp.controllers

import org.apache.logging.log4j.message.Message
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/chat")
class ChatController {

    @PostMapping("/sendMessage")
    fun sendMessage(@RequestBody message: Message): Message {
        return message
    }
}