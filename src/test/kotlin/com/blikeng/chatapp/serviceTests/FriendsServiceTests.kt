package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.FriendDTO
import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.repositories.FriendsRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.services.FriendsService
import com.blikeng.chatapp.services.UserService
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.sql.Date
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class FriendsServiceTests {
    @InjectMockKs
    private lateinit var friendsService: FriendsService

    @MockK
    private lateinit var friendsRepository: FriendsRepository

    @MockK
    private lateinit var userService: UserService

    @MockK
    private lateinit var userRepository: UserRepository

    val user1 = UserEntity(
        id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        username = "username1",
        password = "password",
    )
    val user2 = UserEntity(
        id = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
        username = "username2",
        password = "password",
    )

    val friendsEntity = FriendsEntity(
        id = FriendsId(user1.id, user2.id),
        userA = user1,
        userB = user2
    )

    val friendship = FriendsEntity(
        id = FriendsId(user1.id, user2.id),
        userA = user1,
        userB = user2
    )

    @Test
    fun shouldGetFriendsAsUser1(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { friendsRepository.findFriendsForUser(user1.id) } returns listOf(friendsEntity)

        val friends = friendsService.getFriends()

        assertEquals(friendsEntity.userB.username, friends[0].username)
    }

    @Test
    fun shouldGetFriendsAsUser2(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user2.id, null, emptyList())

        every { userService.getUserById(user2.id) } returns user2
        every { friendsRepository.findFriendsForUser(user2.id) } returns listOf(friendsEntity)

        val friends = friendsService.getFriends()

        assertEquals(friendsEntity.userA.username, friends[0].username)
    }

    @Test
    fun shouldFailToGetFriendsWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriends()
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldAddFriendsAsUser1(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsEntity>()

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendsService.addFriend("username2")

        val saved = slot.captured

        assertEquals(setOf(user1.id, user2.id), setOf(saved.userA.id, saved.userB.id))
    }

    @Test
    fun shouldAddFriendsAsUser2(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user2.id, null, emptyList())

        val slot = slot<FriendsEntity>()

        every { userService.getUserById(user2.id) } returns user2
        every { userRepository.getUserByUsername("username1") } returns user1
        every { friendsRepository.existsById(any()) } returns false
        every { friendsRepository.save(capture(slot)) } answers { firstArg() }

        friendsService.addFriend("username1")

        val saved = slot.captured

        assertEquals(setOf(user1.id, user2.id), setOf(saved.userA.id, saved.userB.id))
    }

    @Test
    fun shouldFailToAddFriendsWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("username")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddNonExistentUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("non existent") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("non existent")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToAddYourselfAsFriend(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username1") } returns user1

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("username1")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToAddFriendWhenAlreadyFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsEntity>()

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

        val exception = assertFailsWith<ApiException> {
            friendsService.addFriend("username2")
        }

        assertEquals(HttpStatus.CONFLICT, exception.status)
    }

    @Test
    fun shouldRemoveFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        val slot = slot<FriendsId>()

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true
        every { friendsRepository.deleteById(capture(slot)) } just Runs

        friendsService.removeFriend("username2")

        val deleted = slot.captured

        assertEquals(setOf(user1.id, user2.id), setOf(deleted.userA, deleted.userB))
    }

    @Test
    fun shouldFailToRemoveFriendsWithInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.removeFriend("username")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToRemoveNonExistentUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("fake name") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.removeFriend("fake name")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToRemoveUserWhoIsNotFriend(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            friendsService.removeFriend("username2")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldGetFriendInfo(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

       val friend = friendsService.getFriendInfo("username2")

        assertEquals(user2.username, friend.username)
        assertEquals(user2.bio, friend.bio)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendInfo("username")
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenUserDoesNotExist(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("fake name") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendInfo("fake name")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetFriendInfoWhenNotFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendInfo("username2")
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldGetFriendEntity(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userRepository.getUserByUsername("username2") } returns user2
        every { friendsRepository.existsById(any()) } returns true

        val friend = friendsService.getFriendEntity("username2", user1.id)

        assertEquals(user2.username, friend.username)
        assertEquals(user2.bio, friend.bio)
    }

    @Test
    fun shouldFailToGetFriendEntityWhenUserDoesNotExist(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("fake name") } returns null

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendEntity("fake name", user1.id)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }

    @Test
    fun shouldFailToGetFriendEntityWhenNotFriends(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(user1.id, null, emptyList())

        every { userService.getUserById(user1.id) } returns user1
        every { userRepository.getUserByUsername("real name") } returns user2
        every { friendsRepository.existsById(any()) } returns false

        val exception = assertFailsWith<ApiException> {
            friendsService.getFriendEntity("real name", user1.id)
        }

        assertEquals(HttpStatus.NOT_FOUND, exception.status)
    }
}