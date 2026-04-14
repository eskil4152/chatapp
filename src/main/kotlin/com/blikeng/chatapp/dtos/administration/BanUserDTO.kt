package com.blikeng.chatapp.dtos.administration

data class BanUserDTO (
    val id: String,
    val reason: String? = null,
)