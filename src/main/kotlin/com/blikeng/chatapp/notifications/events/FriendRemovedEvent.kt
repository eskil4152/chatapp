package com.blikeng.chatapp.notifications.events

import java.util.UUID

data class FriendRemovedEvent(
    val userId: UUID,
    val friendId: UUID,
)
