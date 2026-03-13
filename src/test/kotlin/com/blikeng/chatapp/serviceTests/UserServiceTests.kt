package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.ChangeUserDTO
import com.blikeng.chatapp.dtos.EditPasswordDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.PasswordService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class UserServiceTests {
    // ==========================
    // Tests for UserService. Verifies:
    // - User lookup and self-retrieval
    // - Profile updates
    // - Password updates
    // - Failure cases for invalid users, invalid passwords, and missing authentication
    // ==========================
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

        assertNotNull(user)
    }

    @Test
    fun shouldNotGetUserByInvalidId(){
        every { userRepository.findById(any()) } returns Optional.empty()

        val user = userService.getUserById(UUID.randomUUID())

        assertNull(user)
    }

    @Test
    fun shouldGetSelf(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { roomRepository.findRoomsForUser(any()) } returns emptyList()
        every { userRepository.findById(any()) } returns Optional.of(UserEntity(username = "u", password = ""))

        val self = userService.getSelf()
        assertEquals("u", self.username)
    }

    @Test
    fun shouldNotGetSelfWhenInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            userService.getSelf()
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_USER, exception.message)
    }

    @Test
    fun shouldFailToUpdateUserWhenInvalidUser(){
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO("","","",""))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_USER, exception.message)
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

        val exception = assertFailsWith<ApiException> {
            userService.editPassword(
                EditPasswordDTO(
                    oldPassword = "old password",
                    newPassword = "new password"
                )
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_USER, exception.message)

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

        val exception = assertFailsWith<ApiException> {
            userService.editPassword(
                EditPasswordDTO(
                    oldPassword = "old passworded",
                    newPassword = "new password"
                )
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.WRONG_PASSWORD, exception.message)

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

        val exception = assertFailsWith<ApiException> {
            userService.editPassword(
                EditPasswordDTO(
                    oldPassword = "oldPassword",
                    newPassword = "new"
                )
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.SHORT_PASSWORD, exception.message)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldFailToGetUserWithoutAuthentication() {
        val exception = assertFailsWith<ApiException> {
            userService.getSelf()
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToEditUserWithoutAuthentication() {
        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO("", "", "", ""))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }

    @Test
    fun shouldFailToEditPasswordWithoutAuthentication() {
        val exception = assertFailsWith<ApiException> {
            userService.editPassword(EditPasswordDTO("oldPassword", "newPassword"))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status)
        assertEquals(ErrorMessages.INVALID_TOKEN, exception.message)
    }
}