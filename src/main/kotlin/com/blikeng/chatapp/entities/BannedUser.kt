package com.blikeng.chatapp.entities

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "banned_users")
class BannedUser(
    @Id
    val userId: UUID,
    val bannedBy: UUID,
    val bannedAt: Instant = Instant.now(),
    val reason: String? = null
)