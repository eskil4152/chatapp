package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Entity
@Table(name = "user_rooms")
class UserRoomEntity (
    @EmbeddedId
    var id: UserRoomId,

    @Enumerated(EnumType.STRING)
    var role: RoomRole,

    @Enumerated(EnumType.STRING)
    var type: RoomType,
)

@Embeddable
data class UserRoomId(
    var userId: UUID,
    var roomId: UUID
) : Serializable

enum class RoomRole {
    MEMBER,
    MODERATOR,
    ADMIN,
    OWNER;

    fun isAtLeast(required: RoomRole) = this.ordinal >= required.ordinal
}