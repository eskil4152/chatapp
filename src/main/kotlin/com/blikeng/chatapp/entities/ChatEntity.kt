package com.blikeng.chatapp.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.sql.Timestamp
import java.util.UUID

@Entity
@Table(name = "chats")
class ChatEntity (
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @ManyToOne
    @JoinColumn(name = "room_id")
    val room: RoomEntity,

    @ManyToOne
    @JoinColumn(name = "user_id")
    val user: UserEntity,

    val message: String,

    val timestamp: Timestamp
)