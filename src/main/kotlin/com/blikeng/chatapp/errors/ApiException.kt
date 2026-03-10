package com.blikeng.chatapp.errors

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    message: String
) : RuntimeException(message)