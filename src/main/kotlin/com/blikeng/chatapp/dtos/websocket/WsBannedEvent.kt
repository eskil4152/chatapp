package com.blikeng.chatapp.dtos.websocket

data class WsBannedEvent (
    val type: String = "BANNED",
    val byUsername: String,
    val reason: String,
)