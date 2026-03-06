package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.ErrorMessages.INVALID_PASSWORD
import com.blikeng.chatapp.ErrorMessages.SHORT_PASSWORD
import com.blikeng.chatapp.dtos.ChangeUserDTO
import com.blikeng.chatapp.dtos.EditPasswordDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.security.PasswordService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class UserServiceTests {
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var passwordService: PasswordService

    @InjectMockKs
    lateinit var userService: UserService

    @AfterEach
    fun clearSecurity() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun shouldGetUserById(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

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
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { roomRepository.findRoomsForUser(any()) } returns emptyList()
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))

        userService.getSelf()
    }

    @Test
    fun shouldNotGetSelfWhenNoUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            userService.getSelf()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("User not found", exception.reason)
    }

    @Test
    fun shouldFailToUpdateUserWhenNoUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            userService.editProfile(ChangeUserDTO("","","",""))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)
    }

    @Test
    fun shouldUpdateUser() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        val user = UserEntity(
            id = userId,
            username = "username",
            password = "",
            bio = "oldBio",
            email = "oldEmail",
            fullName = "oldName",
            avatarUrl = "oldAvatar"
        )

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
        )

        val savedUser = slot.captured
        assertEquals("newBio", savedUser.bio)
        assertEquals("newEmail", savedUser.email)
        assertEquals("newFullName", savedUser.fullName)
        assertEquals("newAvatar", savedUser.avatarUrl)

        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun shouldUpdateUserPassword() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "username",
            password = "old password",
        )

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { passwordService.checkPassword(any(), any()) } returns true
        every { passwordService.encodePassword(any()) } returns "encoded"

        val slot = slot<UserEntity>()
        every { userRepository.save(capture(slot)) } answers { slot.captured }

        userService.editPassword(
            EditPasswordDTO(
                oldPassword = "old password",
                newPassword = "new password"
            )
        )

        val savedUser = slot.captured

        assertEquals("encoded", savedUser.password)

        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun shouldFailToUpdateUserPasswordWithInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ResponseStatusException> {
            userService.editPassword(
                EditPasswordDTO(
                    oldPassword = "old password",
                    newPassword = "new password"
                )
            )
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid user", exception.reason)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldFailToUpdateUserPasswordWithWrongPassword() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "username",
            password = "old password",
        )

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { passwordService.checkPassword(any(), any()) } returns false

        val exception = assertFailsWith<ResponseStatusException> {
            userService.editPassword(
                EditPasswordDTO(
                    oldPassword = "old passworded",
                    newPassword = "new password"
                )
            )
        }

        assertEquals(exception.statusCode, HttpStatus.BAD_REQUEST)
        assertEquals(exception.reason, INVALID_PASSWORD)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldFailToUpdateUserPasswordWithTooShortPassword() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "username",
            password = "oldPassword",
        )

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { passwordService.checkPassword(any(), any()) } returns true

        val exception = assertFailsWith<ResponseStatusException> {
            userService.editPassword(
                EditPasswordDTO(
                    oldPassword = "oldPassword",
                    newPassword = "new"
                )
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals(SHORT_PASSWORD, exception.reason)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldFailToGetUserWithoutAuthentication() {
        val exception = assertFailsWith<ResponseStatusException> {
            userService.getSelf()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToEditUserWithoutAuthentication() {
        val exception = assertFailsWith<ResponseStatusException> {
            userService.editProfile(ChangeUserDTO("", "", "", ""))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }

    @Test
    fun shouldFailToEditPasswordWithoutAuthentication() {
        val exception = assertFailsWith<ResponseStatusException> {
            userService.editPassword(EditPasswordDTO("oldPassword", "newPassword"))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("Invalid token", exception.reason)
    }
}