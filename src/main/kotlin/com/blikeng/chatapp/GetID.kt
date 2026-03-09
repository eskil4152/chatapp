package com.blikeng.chatapp

import com.blikeng.chatapp.errors.InvalidTokenException
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

fun getId(): UUID {
    return SecurityContextHolder.getContext().authentication?.principal as? UUID
        ?: throw InvalidTokenException()
}