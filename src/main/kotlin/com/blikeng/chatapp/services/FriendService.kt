package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.friends.FriendDTO
import com.blikeng.chatapp.dtos.websocket.OnlineFriend
import com.blikeng.chatapp.dtos.websocket.WsFriendPresence
import com.blikeng.chatapp.dtos.websocket.WsFriendSnapshot
import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.AlreadyFriendsException
import com.blikeng.chatapp.errors.FriendYourselfException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.messaging.redis.PresenceHandler
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.auth.getId
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*

// ==========================
// Handles friend relationships, friend lookups, and friend profile retrieval.
// Validates friendship existence and enforces deterministic friendship ordering.
// ==========================
@Service
class FriendService(
    private val friendsRepository: FriendsRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val presenceHandler: PresenceHandler,
    private val objectMapper: ObjectMapper,
    private val redisTemplate: RedisTemplate<String, String>
) {
    fun getFriends(): List<FriendDTO> {
        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        val friends = friendsRepository.findFriendsForUser(id)

        return friends.map { friendship ->
            val friend = if (friendship.userA.id == id) friendship.userB else friendship.userA

            FriendDTO (
                userId = friend.id,
                username = friend.username,
                bio = friend.bio,
                email = friend.email,
                fullName = friend.fullName,
                avatarUrl = friend.avatarUrl,
                birthday = friend.birthday,
                createdAt = friend.createdAt,
            )
        }
    }

    fun areFriends(userA: UUID, userB: UUID): Boolean {
        val id = generateFriendshipId(userA, userB)
        return friendsRepository.existsById(id)
    }

    fun addFriend(id: UUID, friendId: UUID) {
        val friendshipId = generateFriendshipId(id, friendId)

        val user = userService.getUserById(id) ?: throw InvalidUserException()
        val friend = userService.getUserById(friendId) ?: throw InvalidUserException()

        val (userA, userB) = orderedUsers(user, friend)
        friendsRepository.save(FriendsEntity(
            id = friendshipId,
            userA = userA,
            userB = userB
        ))
    }

    fun removeFriend(friendUsername: String) {
        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        val friend = userRepository.getUserByUsernameIgnoreCase(friendUsername) ?: throw UserNotFoundException()

        val friendshipId = generateFriendshipId(id, friend.id)
        if (!friendsRepository.existsById(friendshipId)) throw UserNotFoundException()

        friendsRepository.deleteById(friendshipId)
    }

    fun getFriendInfo(friendUsername: String): FriendDTO {
        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        val friend = userRepository.getUserByUsernameIgnoreCase(friendUsername) ?: throw UserNotFoundException()

        val friendshipId = generateFriendshipId(id, friend.id)
        if (!friendsRepository.existsById(friendshipId)) throw UserNotFoundException()

        return FriendDTO(
            userId = friend.id,
            username = friend.username,
            bio = friend.bio,
            email = friend.email,
            fullName = friend.fullName,
            avatarUrl = friend.avatarUrl,
            birthday = friend.birthday,
            createdAt = friend.createdAt,
        )
    }

    fun getFriendEntity(username: String, userId: UUID): UserEntity {
        val friend = userRepository.getUserByUsernameIgnoreCase(username) ?: throw UserNotFoundException()

        val friendshipId = generateFriendshipId(userId, friend.id)
        if (!friendsRepository.existsById(friendshipId)) throw UserNotFoundException()

        return friend
    }

    fun notifyFriends(userId: UUID, online: Boolean) {
        val payload = objectMapper.writeValueAsString(
            WsFriendPresence(
                userId = userId,
                online = online
            )
        )

        for (friendship in friendsRepository.findFriendsForUser(userId)) {
            val friendId = if (friendship.userA.id == userId) {
                friendship.userB.id
            } else {
                friendship.userA.id
            }

            redisTemplate.convertAndSend("user:${friendId}", payload)
        }
    }

    fun getOnlineFriends(userId: UUID, session: WebSocketSession){
        val friends = mutableListOf<OnlineFriend>()

        for (friendship in friendsRepository.findFriendsForUser(userId)) {
            val friend = if (friendship.userA.id == userId) {
                friendship.userB
            } else {
                friendship.userA
            }

            friends.add(OnlineFriend(
                userId = friend.id,
                username = friend.username,
                avatarUrl = friend.avatarUrl,
                online = presenceHandler.isUserOnline(friend.id)
            ))
        }

        synchronized(session) {
            if (session.isOpen) {
                session.sendMessage(
                    TextMessage(
                        objectMapper.writeValueAsString(
                            WsFriendSnapshot(friends = friends)
                        )
                    )
                )
            }
        }
    }

    // ==========================
    // Internal helpers
    // ==========================
    private fun generateFriendshipId(user1: UUID, user2: UUID): FriendsId {
        if (user1 == user2) throw FriendYourselfException()

        return if (user1.toString() < user2.toString()) {
            FriendsId(user1, user2)
        } else {
            FriendsId(user2, user1)
        }
    }

    private fun orderedUsers(user1: UserEntity, user2: UserEntity): Pair<UserEntity, UserEntity> {
        return if (user1.id.toString() < user2.id.toString()) {
            user1 to user2
        } else {
            user2 to user1
        }
    }
}