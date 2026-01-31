package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.DTOs.ChangeUserDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class UserServiceTests {
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var jwtService: JwtService

    @InjectMockKs
    lateinit var userService: UserService

    @Test
    fun shouldGetUserById(){
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))

        val user = userService.getUserById(UUID.randomUUID())

        assert(user != null)
    }

    @Test
    fun shouldNotGetUserByInvalidId(){
        every { userRepository.findById(any()) } returns Optional.empty()

        val user = userService.getUserById(UUID.randomUUID())

        assert(user == null)
    }

    @Test
    fun shouldGetSelf(){
        every { jwtService.validateToken(any()) } returns Pair("", UUID.randomUUID())
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))

        val user = userService.getSelf("token")
        assert(user != null)
    }

    @Test
    fun shouldNotGetSelfWhenInvalidCookie(){
        every { jwtService.validateToken(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            userService.getSelf("token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldNotGetSelfWhenNoUser(){
        every { jwtService.validateToken(any()) } returns Pair("", UUID.randomUUID())
        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            userService.getSelf("token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("User not found", exception.reason)
    }

    @Test
    fun shouldFailToUpdateUserWhenInvalidCookie(){
        every { jwtService.validateToken(any()) } returns null

        val exception = assertFailsWith<ResponseStatusException> {
            userService.editProfile(ChangeUserDTO("","","",""), "token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToUpdateUserWhenNoUser(){
        every { jwtService.validateToken(any()) } returns Pair("", UUID.randomUUID())
        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            userService.editProfile(ChangeUserDTO("","","",""), "token")
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)
    }

    @Test
    fun shouldUpdateUser() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "u",
            password = "",
            bio = "oldBio",
            email = "oldEmail",
            fullName = "oldName",
            avatarUrl = "oldAvatar"
        )

        every { jwtService.validateToken(any()) } returns Pair("", userId)
        every { userRepository.findById(userId) } returns Optional.of(user)

        val slot = slot<UserEntity>()
        every { userRepository.save(capture(slot)) } answers { slot.captured }

        userService.editProfile(
            ChangeUserDTO(
                bio = "newBio",
                email = "newEmail",
                fullName = "newFullName",
                avatarUrl = "newAvatar"
            ),
            "token"
        )

        val savedUser = slot.captured
        assertEquals("newBio", savedUser.bio)
        assertEquals("newEmail", savedUser.email)
        assertEquals("newFullName", savedUser.fullName)
        assertEquals("newAvatar", savedUser.avatarUrl)

        verify(exactly = 1) { userRepository.save(any()) }
    }
}