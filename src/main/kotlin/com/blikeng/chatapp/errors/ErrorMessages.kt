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
    const val LONG_USERNAME = "Username must be at most 32 characters long"
    const val LONG_PASSWORD = "Password must be at most 128 characters long"
    const val INVALID_FIELD = "One or more fields contain invalid values"

    const val INVALID_UUID = "Invalid UUID"

    const val USER_NOT_FOUND = "User not found"
    const val ALREADY_FRIEND = "Already friends"
    const val CANT_FRIEND_YOURSELF = "You can not friend yourself"

    const val INVALID_PARAMETERS = "Invalid parameters"

    const val BANNED = "You have been banned from this room"
    const val INVALID_BAN = "You can not ban this user"

    const val INVITE_NOT_FOUND = "Invite not found"
    const val INVALID_INVITE = "Invalid invite"
    const val ALREADY_INVITED = "Already invited"
}