package com.blikeng.chatapp.entities

import com.blikeng.chatapp.security.UserRole
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity(
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
    val createdAt: Instant = Instant.now(),
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    var role: UserRole = UserRole.USER,
)
