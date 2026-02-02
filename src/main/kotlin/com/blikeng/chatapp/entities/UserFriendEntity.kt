package com.blikeng.chatapp.entities

import jakarta.persistence.*
import java.io.Serializable
import java.util.*

@Entity
@Table(name = "user_friends")
class UserFriendEntity (
    @EmbeddedId
    val id: UserFriendId,

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    val user: UserEntity,

    @ManyToOne
    @MapsId("friendId")
    @JoinColumn(name = "friend_id")
    val friend: UserEntity
)

@Embeddable
data class UserFriendId(
    val userId: UUID,
    val friendId: UUID
) : Serializable