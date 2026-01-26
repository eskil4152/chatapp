package com.blikeng.chatapp.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

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

enum class RoomType {
    DIRECT, GROUP
}

enum class RoomRole {
    OWNER, MEMBER
}