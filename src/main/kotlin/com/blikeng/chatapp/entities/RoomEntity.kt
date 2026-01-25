package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    @GeneratedValue
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    val name: String,

    @ManyToMany(mappedBy = "rooms")
    val users: MutableSet<UserEntity> = mutableSetOf()
)