package com.blikeng.chatapp.errors

import org.springframework.http.HttpStatus

// ==========================
// Application-specific API exceptions mapped to HTTP status codes.
// Used throughout services to signal expected client-facing errors.
// ==========================

// User-related errors
class InvalidTokenException : ApiException(HttpStatus.UNAUTHORIZED, ErrorMessages.INVALID_TOKEN)
class InvalidCredentialsException : ApiException(HttpStatus.UNAUTHORIZED, ErrorMessages.INVALID_CREDENTIALS)
class WrongPasswordException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.WRONG_PASSWORD)
class InvalidUserException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_USER)
class UsernameAlreadyExistsException : ApiException(HttpStatus.CONFLICT, ErrorMessages.USERNAME_EXISTS)

// Room-related errors
class RoomNotFoundException : ApiException(HttpStatus.NOT_FOUND, ErrorMessages.ROOM_NOT_FOUND)
class NotPermittedException : ApiException(HttpStatus.FORBIDDEN, ErrorMessages.NOT_PERMITTED)
class InvalidRoomNameException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_ROOM_NAME)

// Input validation errors
class ShortPasswordException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.SHORT_PASSWORD)
class ShortUsernameException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.SHORT_USERNAME)
class LongUsernameException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.LONG_USERNAME)
class LongPasswordException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.LONG_PASSWORD)
class InvalidUUIDException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_UUID)
class InvalidMessageException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_MESSAGE)
class InvalidParametersException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_PARAMETERS)
class InvalidFieldException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_FIELD)

// Friends
class UserNotFoundException : ApiException(HttpStatus.NOT_FOUND, ErrorMessages.USER_NOT_FOUND)
class AlreadyFriendsException : ApiException(HttpStatus.CONFLICT, ErrorMessages.ALREADY_FRIEND)
class FriendYourselfException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.CANT_FRIEND_YOURSELF)

// Bans
class BannedException : ApiException(HttpStatus.FORBIDDEN, ErrorMessages.BANNED)
class InvalidBanException : ApiException(HttpStatus.FORBIDDEN, ErrorMessages.INVALID_BAN)

// Invites
class InvalidInviteException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVALID_INVITE)
class InviteNotFoundException : ApiException(HttpStatus.NOT_FOUND, ErrorMessages.INVITE_NOT_FOUND)
class AlreadyInvitedException : ApiException(HttpStatus.CONFLICT, ErrorMessages.ALREADY_INVITED)
class InviteYourselfException : ApiException(HttpStatus.BAD_REQUEST, ErrorMessages.INVITE_YOURSELF)
class InviteBannedUserException : ApiException(HttpStatus.FORBIDDEN, ErrorMessages.INVITE_BANNED)