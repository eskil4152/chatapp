package com.blikeng.chatapp.dtos.administration;

data class SiteInfoDTO (
    val connectedUsers: Double,
    val totalSessions: Double,
    val activeRooms: Double,
    val totalUsers: Long,
    val totalRooms: Long,
    val bannedUsers: Long,
)