package com.blikeng.chatapp.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Entity
@Table(name = "room_bans")
class RoomBan(
    @EmbeddedId
    val id: RoomBanId,
)

@Embeddable
data class RoomBanId(
    val userId: UUID,
    val roomId: UUID,
) : Serializable
