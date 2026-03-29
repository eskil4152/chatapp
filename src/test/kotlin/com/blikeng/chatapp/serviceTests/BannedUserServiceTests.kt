package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.entities.BannedUserId
import com.blikeng.chatapp.repositories.BannedUserRepository
import com.blikeng.chatapp.services.BannedUserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExtendWith(MockKExtension::class)
class BannedUserServiceTests {
    // ==========================
    // Tests for BannedUserService. Verifies:
    // - Banned status check returns true/false correctly
    // - banUser saves a BannedUser entity
    // - unbanUser deletes the BannedUser entity
    // ==========================

    @MockK lateinit var bannedUserRepository: BannedUserRepository

    @InjectMockKs lateinit var bannedUserService: BannedUserService

    @Test
    fun shouldReturnTrueWhenUserIsBanned() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { bannedUserRepository.existsById(any()) } returns true

        assertTrue(bannedUserService.isUserBanned(userId, roomId))
    }

    @Test
    fun shouldReturnFalseWhenUserIsNotBanned() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { bannedUserRepository.existsById(any()) } returns false

        assertFalse(bannedUserService.isUserBanned(userId, roomId))
    }

    @Test
    fun shouldBanUser() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { bannedUserRepository.save(any()) } answers { firstArg() }

        bannedUserService.banUser(userId, roomId)

        verify(exactly = 1) { bannedUserRepository.save(match { it.id.userId == userId && it.id.roomId == roomId }) }
    }

    @Test
    fun shouldUnbanUser() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { bannedUserRepository.deleteById(any()) } just Runs

        bannedUserService.unbanUser(userId, roomId)

        verify(exactly = 1) { bannedUserRepository.deleteById(match { it.userId == userId && it.roomId == roomId }) }
    }

    @Test
    fun shouldGetBannedUserIds() {
        val roomId = UUID.randomUUID()
        val bannedUsers: List<BannedUser> = listOf(
            BannedUser(BannedUserId(UUID.randomUUID(), roomId)),
            BannedUser(BannedUserId(UUID.randomUUID(), roomId))
        )

        every { bannedUserRepository.findAllByIdRoomId(roomId) } returns bannedUsers

        val userIds = bannedUserService.getBannedUserIds(roomId)

        assertEquals(userIds.size, 2)
        assertTrue(userIds.containsAll(bannedUsers.map { it.id.userId }))
    }
}
