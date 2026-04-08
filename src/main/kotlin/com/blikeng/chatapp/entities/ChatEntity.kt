package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.time.Instant
import java.util.*

@Entity
@Table(name = "chats")
class ChatEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "room_id", nullable = false)
    val roomId: UUID,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserEntity,

    @Column(nullable = false)
    val timestamp: Instant = Instant.now(),

    @Column(columnDefinition = "TEXT")
    var message: String? = null,

    var ciphertext: ByteArray? = null,
    var nonce: ByteArray? = null,
    var keyVersion: Int? = null,
)