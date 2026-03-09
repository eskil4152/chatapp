package com.blikeng.chatapp.errors

import org.springframework.http.HttpStatus

// User related errors
class InvalidTokenException : ApiException(HttpStatus.UNAUTHORIZED, ErrorMessages.INVALID_TOKEN)
class InvalidCredentialsException : ApiException(HttpStatus.UNAUTHORIZED, ErrorMessages.INVALID_CREDENTIALS)
class WrongPasswordException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.WRONG_PASSWORD)
class InvalidUserException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_USER)
class UsernameAlreadyExistsException : ApiException(HttpStatus.CONFLICT, ErrorMessages.USERNAME_EXISTS)

// Room related errors
class RoomNotFoundException : ApiException(HttpStatus.NOT_FOUND, ErrorMessages.ROOM_NOT_FOUND)
class NotPermittedException : ApiException(HttpStatus.FORBIDDEN, ErrorMessages.NOT_PERMITTED)
class InvalidRoomNameException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_ROOM_NAME)

// Input validation errors
class ShortPasswordException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.SHORT_PASSWORD)
class ShortUsernameException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.SHORT_USERNAME)
class InvalidUUIDException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_UUID)
