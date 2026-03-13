package com.blikeng.chatapp.dtos.user

data class EditPasswordDTO (
    val oldPassword: String,
    val newPassword: String
)