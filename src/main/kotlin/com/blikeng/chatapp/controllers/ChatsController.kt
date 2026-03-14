package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.messaging.SendMessageDTO
import com.blikeng.chatapp.errors.InvalidUUIDException
import com.blikeng.chatapp.services.ChatService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/chats")
class ChatsController(
    private val chatService: ChatService
) {
    @GetMapping("/{roomId}")
    fun getRoomMessages(
        @PathVariable roomId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int
    ): List<SendMessageDTO> {
        val roomUUID = try {
            UUID.fromString(roomId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        return chatService.getRoomMessages(roomUUID, page, size)
    }
}