package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.entities.RoomBan
import com.blikeng.chatapp.entities.RoomBanId
import com.blikeng.chatapp.repositories.RoomBanRepository
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

@ExtendWith(MockKExtension::class)
class RoomBanServiceTests {
    // ==========================
    // Tests for BannedUserService. Verifies:
    // - Banned status check returns true/false correctly
    // - banUser saves a BannedUser entity
    // - unbanUser deletes the BannedUser entity
    // ==========================

    @MockK lateinit var roomBanRepository: RoomBanRepository

    @InjectMockKs lateinit var bannedUserService: BannedUserService

    @Test
    fun shouldReturnTrueWhenUserIsBanned() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { roomBanRepository.existsById(any()) } returns true

        assertTrue(bannedUserService.isUserBanned(userId, roomId))
    }

    @Test
    fun shouldReturnFalseWhenUserIsNotBanned() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { roomBanRepository.existsById(any()) } returns false

        assertFalse(bannedUserService.isUserBanned(userId, roomId))
    }

    @Test
    fun shouldBanUser() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { roomBanRepository.save(any()) } answers { firstArg() }

        bannedUserService.banUser(userId, roomId)

        verify(exactly = 1) { roomBanRepository.save(match { it.id.userId == userId && it.id.roomId == roomId }) }
    }

    @Test
    fun shouldUnbanUser() {
        val userId = UUID.randomUUID()
        val roomId = UUID.randomUUID()

        every { roomBanRepository.deleteById(any()) } just Runs

        bannedUserService.unbanUser(userId, roomId)

        verify(exactly = 1) { roomBanRepository.deleteById(match { it.userId == userId && it.roomId == roomId }) }
    }

    @Test
    fun shouldGetBannedUserIds() {
        val roomId = UUID.randomUUID()
        val roomBans: List<RoomBan> = listOf(
            RoomBan(RoomBanId(UUID.randomUUID(), roomId)),
            RoomBan(RoomBanId(UUID.randomUUID(), roomId))
        )

        every { roomBanRepository.findAllByIdRoomId(roomId) } returns roomBans

        val userIds = bannedUserService.getBannedUserIds(roomId)

        assertEquals(userIds.size, 2)
        assertTrue(userIds.containsAll(roomBans.map { it.id.userId }))
    }
}
