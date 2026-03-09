package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.FriendDTO
import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.AlreadyFriendsException
import com.blikeng.chatapp.errors.FriendYourselfException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.NotFriendsException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.getId
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FriendsService(
    private val friendsRepository: FriendsRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
) {
    fun getFriends(): List<FriendDTO> {
        val id = getId()
        userService.getUserById(id) ?: throw InvalidUserException()

        val friends = friendsRepository.findFriendsForUser(id)

        return friends.map { friend ->
            FriendDTO (
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

    fun addFriend(friendUsername: String) {
        val id = getId()
        val user = userService.getUserById(id) ?: throw InvalidUserException()

        val friend = userRepository.getUserByUsername(friendUsername) ?: throw UserNotFoundException()

        val friendshipId = generateFriendshipId(id, friend.id)
        if (friendsRepository.existsById(friendshipId)) throw AlreadyFriendsException()

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

        val friend = userRepository.getUserByUsername(friendUsername) ?: throw UserNotFoundException()

        val friendshipId = generateFriendshipId(id, friend.id)
        if (!friendsRepository.existsById(friendshipId)) throw NotFriendsException()

        friendsRepository.deleteById(friendshipId)
    }

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