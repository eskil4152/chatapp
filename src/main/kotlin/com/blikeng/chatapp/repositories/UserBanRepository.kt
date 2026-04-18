package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.security.UserRole
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface UserBanRepository : JpaRepository<BannedUser, UUID> {

    @Query("""
        SELECT
            b.user_id        AS userId,
            u.username       AS username,
            b.banned_by      AS bannedBy,
            ban.username     AS bannedByUsername,
            ban.role         AS bannedByRole,
            b.banned_at      AS bannedAt,
            b.reason         AS reason
        FROM banned_users b
        JOIN users u   ON u.id  = b.user_id
        JOIN users ban ON ban.id = b.banned_by
        ORDER BY b.banned_at DESC
    """, nativeQuery = true)
    fun findAllWithUsers(pageable: Pageable): Page<BanProjection>
}

interface BanProjection {
    val userId: UUID
    val username: String
    val bannedBy: UUID
    val bannedByUsername: String
    val bannedByRole: UserRole
    val bannedAt: Instant
    val reason: String?
}