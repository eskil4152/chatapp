package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false)
    var name: String,

    val encrypted: Boolean = false,
    val keyVersion: Int? = null,

    val createdAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    val type: RoomType
)

enum class RoomType {
    GROUP,
    PRIVATE
}