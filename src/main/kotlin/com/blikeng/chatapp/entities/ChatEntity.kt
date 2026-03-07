package com.blikeng.chatapp.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.sql.Timestamp
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
    val timestamp: Timestamp = Timestamp(System.currentTimeMillis()),

    @Column(columnDefinition = "TEXT")
    var message: String? = null,

    var ciphertext: ByteArray? = null,
    var nonce: ByteArray? = null,
    var keyVersion: Int? = null,
)