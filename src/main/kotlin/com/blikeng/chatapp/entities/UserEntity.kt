package com.blikeng.chatapp.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.util.*

@Entity
@Table(name = "users")
class UserEntity (
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(unique = true, nullable = false)
    val username: String,

    @Column(name = "password", nullable = false)
    var password: String,

    @Column(name = "bio")
    var bio: String? = null,

    @Column(name = "email")
    var email: String? = null,

    @Column(name = "full_name")
    var fullName: String? = null,

    @Column(name = "avatar_url")
    var avatarUrl: String? = null,

    @Column(name = "birthday")
    val birthday: Date? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Timestamp = Timestamp(System.currentTimeMillis()),
)