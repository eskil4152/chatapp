package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Entity
@Table(name = "user_rooms")
class UserRoomEntity (
    @EmbeddedId
    val id: UserRoomId,

    @ManyToOne
    @MapsId("userId")
    val user: UserEntity,

    @ManyToOne
    @MapsId("roomId")
    val room: RoomEntity,

    @Enumerated(EnumType.STRING)
    val role: RoomRole
)

@Embeddable
class UserRoomId(
    val userId: UUID,
    val roomId: UUID
) : Serializable

enum class RoomRole {
    OWNER, MEMBER
}