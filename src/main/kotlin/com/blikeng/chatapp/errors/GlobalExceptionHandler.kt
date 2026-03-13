package com.blikeng.chatapp.errors

import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

// ==========================
// Centralized exception handler for REST endpoints.
// Converts ApiException instances into HTTP responses and
// logs unexpected exceptions as internal server errors.
// ==========================
@RestControllerAdvice
class GlobalExceptionHandler {
    val logger: Logger = getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<String> {
        return ResponseEntity
            .status(ex.status)
            .body(ex.message)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknown(ex: Exception): ResponseEntity<String> {
        logger.error("Unhandled exception", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body("Unexpected error")
    }
}