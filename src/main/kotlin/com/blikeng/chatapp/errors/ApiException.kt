package com.blikeng.chatapp.errors

import org.springframework.http.HttpStatus

// ==========================
// Base exception type for application errors.
// Carries the HTTP status code and message returned to clients.
// ==========================
open class ApiException(
    val status: HttpStatus,
    message: String
) : RuntimeException(message)