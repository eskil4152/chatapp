package com.blikeng.chatapp.notifications.events

import java.util.UUID

data class UserBannedEvent(
    val userId: UUID,
    val byUsername: String,
    val reason: String,
)
