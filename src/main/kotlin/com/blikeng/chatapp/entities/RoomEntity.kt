package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "rooms")
class RoomEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val name: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    val owner: UserEntity,

    @ManyToMany(mappedBy = "rooms")
    val users: MutableSet<UserEntity> = mutableSetOf()
)