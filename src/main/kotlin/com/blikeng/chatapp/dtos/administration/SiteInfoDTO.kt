package com.blikeng.chatapp.dtos.administration;

data class SiteInfoDTO (
    val connectedUsers: Double,
    val totalSessions: Double,
    val activeRooms: Double,
    val totalUsers: Int,
    val totalRooms: Int,
    val bannedUsers: Int,
)