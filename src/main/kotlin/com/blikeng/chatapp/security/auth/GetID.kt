package com.blikeng.chatapp.security.auth

import com.blikeng.chatapp.errors.InvalidTokenException
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*

// ==========================
// Extracts the authenticated user ID from the Spring Security context.
// Throws InvalidTokenException if authentication is missing or invalid.
// ==========================
fun getId(): UUID {
    return SecurityContextHolder.getContext().authentication?.principal as? UUID
        ?: throw InvalidTokenException()
}