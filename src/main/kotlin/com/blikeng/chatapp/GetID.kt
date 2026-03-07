package com.blikeng.chatapp

import com.blikeng.chatapp.ErrorMessages.INVALID_TOKEN
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

fun getId(): UUID {
    return SecurityContextHolder.getContext().authentication?.principal as? UUID
        ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_TOKEN)
}