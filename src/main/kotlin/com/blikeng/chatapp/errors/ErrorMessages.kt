package com.blikeng.chatapp.errors

// ==========================
// Centralized constants for API error messages returned to clients.
// Ensures consistent error responses across the application.
// ==========================
object ErrorMessages {
    const val INVALID_TOKEN = "Invalid token"
    const val INVALID_CREDENTIALS = "Invalid credentials"
    const val WRONG_PASSWORD = "Wrong password"
    const val INVALID_USER = "Invalid user"
    const val USERNAME_EXISTS = "Username already exists"

    const val ROOM_NOT_FOUND = "Room not found"
    const val NOT_PERMITTED = "Not permitted"
    const val INVALID_ROOM_NAME = "Invalid room name"

    const val SHORT_PASSWORD = "Password must be at least 8 characters long"
    const val SHORT_USERNAME = "Username must be at least 3 characters long"
    const val INVALID_MESSAGE = "Invalid message"

    const val INVALID_UUID = "Invalid UUID"

    const val USER_NOT_FOUND = "User not found"
    const val ALREADY_FRIEND = "Already friends"
    const val CANT_FRIEND_YOURSELF = "You can't friend yourself"
}