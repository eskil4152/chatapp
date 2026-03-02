package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.sql.Timestamp
import java.util.*

@Entity
@Table(name = "chats")
class ChatEntity (
    @Id
    val id: UUID = UUID.randomUUID(),

    @ManyToOne
    @JoinColumn(name = "room_id")
    val room: RoomEntity,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserEntity,

    val timestamp: Timestamp,

    var message: String? = null,
    var ciphertext: ByteArray? = null,
    var nonce: ByteArray? = null,
    var keyVersion: Int? = null,
)