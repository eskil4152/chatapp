package com.blikeng.chatapp.entities

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.util.UUID

@Entity
@Table(name = "invites")
class InviteEntity (
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    val type: InviteType,

    val fromUserId: UUID,

    val toUserId: UUID? = null,

    val roomId: UUID? = null,

    val usages: Int? = null,

    val maxUsages: Int? = null,

    val expiresAt: Timestamp,

    val status: InviteStatus
) {
    enum class InviteType {
        FRIEND_REQUEST,
        ROOM_INVITE,
        OPEN_ROOM_INVITE
    }

    enum class InviteStatus {
        PENDING,
        ACCEPTED,
        REJECTED
    }
}