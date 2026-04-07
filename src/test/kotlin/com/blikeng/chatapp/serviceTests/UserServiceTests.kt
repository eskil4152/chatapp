package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.dtos.user.ChangeUserDTO
import com.blikeng.chatapp.dtos.user.EditPasswordDTO
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.errors.ApiException
import com.blikeng.chatapp.errors.ErrorMessages
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.auth.PasswordService
import com.blikeng.chatapp.services.UserService
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
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

        userService.editProfile(
            ChangeUserDTO(
                bio = "newBio",
                email = "new@email.com",
                fullName = "newFullName",
                avatarUrl = "newAvatar"
            ),
        )

        assertEquals("newBio", user.bio)
        assertEquals("new@email.com", user.email)
        assertEquals("newFullName", user.fullName)
        assertEquals("newAvatar", user.avatarUrl)
    }

    @Test
    fun shouldUpdateUserTrimmingFields() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        val user = UserEntity(id = userId, username = "username", password = "")
        every { userRepository.findById(userId) } returns Optional.of(user)

        userService.editProfile(
            ChangeUserDTO(
                bio = "  my bio  ",
                email = "  user@example.com  ",
                fullName = "  Full Name  ",
                avatarUrl = "  https://example.com/avatar.png  "
            )
        )

        assertEquals("my bio", user.bio)
        assertEquals("user@example.com", user.email)
        assertEquals("Full Name", user.fullName)
        assertEquals("https://example.com/avatar.png", user.avatarUrl)
    }

    @Test
    fun shouldFailToUpdateUserWithTooLongBio() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(UserEntity(id = userId, username = "username", password = ""))

        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO(bio = "a".repeat(501), email = "", fullName = "", avatarUrl = ""))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_FIELD, exception.message)
    }

    @Test
    fun shouldFailToUpdateUserWithTooLongEmail() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(UserEntity(id = userId, username = "username", password = ""))

        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO(bio = "", email = "a".repeat(250) + "@b.com", fullName = "", avatarUrl = ""))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_FIELD, exception.message)
    }

    @Test
    fun shouldFailToUpdateUserWithInvalidEmailFormat() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(UserEntity(id = userId, username = "username", password = ""))

        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO(bio = "", email = "notanemail", fullName = "", avatarUrl = ""))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_FIELD, exception.message)
    }

    @Test
    fun shouldUpdateUserWithBlankEmail() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        val user = UserEntity(id = userId, username = "username", password = "", email = "old@email.com")
        every { userRepository.findById(userId) } returns Optional.of(user)

        userService.editProfile(ChangeUserDTO(bio = "", email = "", fullName = "", avatarUrl = ""))

        assertEquals("", user.email)
    }

    @Test
    fun shouldFailToUpdateUserWithTooLongFullName() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(UserEntity(id = userId, username = "username", password = ""))

        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO(bio = "", email = "", fullName = "a".repeat(101), avatarUrl = ""))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_FIELD, exception.message)
    }

    @Test
    fun shouldFailToUpdateUserWithTooLongAvatarUrl() {
        val userId = UUID.randomUUID()
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(UserEntity(id = userId, username = "username", password = ""))

        val exception = assertFailsWith<ApiException> {
            userService.editProfile(ChangeUserDTO(bio = "", email = "", fullName = "", avatarUrl = "a".repeat(501)))
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_FIELD, exception.message)
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

        userService.editPassword(
            EditPasswordDTO(
                oldPassword = "old password",
                newPassword = "new password"
            )
        )

        assertEquals("encoded", user.password)
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

    @Test
    fun shouldDeleteUser() {
        val userId = UUID.randomUUID()
        val user = UserEntity(
            id = userId,
            username = "username",
            password = "old password",
        )

        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId, null, emptyList())

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.delete(user) } just Runs

        userService.deleteUser()

        verify(exactly = 1) { userRepository.delete(user) }
    }

    @Test
    fun shouldFailToDeleteInvalidUser() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(UUID.randomUUID(), null, emptyList())

        every { userRepository.findById(any()) } returns Optional.empty()

        val exception = assertFailsWith<ApiException> {
            userService.deleteUser()
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.status)
        assertEquals(ErrorMessages.INVALID_USER, exception.message)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun shouldGetAllUsersById(){
        val user1 = UserEntity(username = "u1", password = "")
        val user2 = UserEntity(username = "u2", password = "")
        val user3 = UserEntity(username = "u3", password = "")

        every { userRepository.findAllById(listOf(user1.id, user2.id, user3.id)) } returns listOf(user1, user2, user3)

        val users = userService.getAllById(listOf(user1.id, user2.id, user3.id))

        assertEquals(
            listOf(user1, user2, user3),
            users
        )
    }
}