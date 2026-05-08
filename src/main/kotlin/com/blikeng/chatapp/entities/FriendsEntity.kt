package com.blikeng.chatapp.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "friends")
class FriendsEntity(
    @EmbeddedId
    val id: FriendsId,
    @ManyToOne
    @MapsId("userA")
    @JoinColumn(name = "user_a")
    val userA: UserEntity,
    @ManyToOne
    @MapsId("userB")
    @JoinColumn(name = "user_b")
    val userB: UserEntity,
    val friendsSince: Instant = Instant.now(),
)

@Embeddable
data class FriendsId(
    val userA: UUID,
    val userB: UUID,
) : Serializable
