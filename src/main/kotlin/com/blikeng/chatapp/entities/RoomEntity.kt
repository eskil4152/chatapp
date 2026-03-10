package com.blikeng.chatapp.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    var name: String,

    val encrypted: Boolean = false,
    val keyVersion: Int? = null,

    @Enumerated(EnumType.STRING)
    val type: RoomType = RoomType.GROUP
)

enum class RoomType {
    GROUP,
    PRIVATE
}