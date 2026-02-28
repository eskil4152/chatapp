package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    val name: String,

    val encrypted: Boolean = false,
    val keyVersion: Int?
)