package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.time.Instant
import java.util.*

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

    var usages: Int? = null,

    val maxUsages: Int? = null,

    val expiresAt: Instant,

    @Enumerated(EnumType.STRING)
    var status: InviteStatus
)
enum class InviteStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    EXHAUSTED
}

enum class InviteType {
    FRIEND_REQUEST,
    ROOM_INVITE,
    OPEN_ROOM_INVITE
}