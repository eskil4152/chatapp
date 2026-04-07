package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.RoomRole

object RoomPermissions {
    val DELETE_ROOM = RoomRole.OWNER
    val EDIT_ROOM = RoomRole.ADMIN
    val BAN_USER = RoomRole.ADMIN
    val UNBAN_USER = RoomRole.ADMIN
    val VIEW_BANS = RoomRole.ADMIN
    val KICK_USER = RoomRole.MODERATOR
    val INVITE_USER = RoomRole.MODERATOR

    val PROMOTE_TO_ADMIN = RoomRole.OWNER
    val DEMOTE_TO_MODERATOR = RoomRole.OWNER
    val PROMOTE_TO_MODERATOR = RoomRole.ADMIN
    val DEMOTE_TO_MEMBER = RoomRole.ADMIN
}