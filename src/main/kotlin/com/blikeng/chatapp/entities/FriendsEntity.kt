package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Entity
@Table(name = "friends")
class FriendsEntity (
    @EmbeddedId
    val id: FriendsId,

    @ManyToOne
    @MapsId("userA")
    @JoinColumn(name = "user_a")
    val userA: UserEntity,

    @ManyToOne
    @MapsId("userB")
    @JoinColumn(name = "user_b")
    val userB: UserEntity
)

@Embeddable
class FriendsId(
    val userA: UUID,
    val userB: UUID
): Serializable