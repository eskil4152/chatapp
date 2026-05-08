package com.blikeng.chatapp.entities

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

@Entity
@Table(name = "user_rooms")
class UserRoomEntity(
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
    var roomId: UUID,
) : Serializable

enum class RoomRole {
    MEMBER,
    MODERATOR,
    ADMIN,
    OWNER,
    ;

    fun isAtLeast(required: RoomRole) = this.ordinal >= required.ordinal
}
