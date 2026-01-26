package com.blikeng.chatapp.entities

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.sql.Timestamp
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "users")
class UserEntity (
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: UUID? = null,

    @Column(unique = true, nullable = false)
    val username: String,

    @Column(name = "password", nullable = false)
    val password: String,

    @Column(name = "bio")
    val bio: String? = null,

    @Column(name = "email")
    val email: String? = null,

    @Column(name = "full_name")
    val fullName: String? = null,

    @Column(name = "avatar_url")
    val avatarUrl: String? = null,

    @Column(name = "birthday")
    val birthday: Date? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Timestamp = Timestamp(System.currentTimeMillis()),

    @ManyToMany
    @JoinTable(
        name = "user_rooms",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "room_id")]
    )
    val rooms: MutableSet<RoomEntity> = mutableSetOf(),

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val friends: MutableSet<UserFriendEntity> = mutableSetOf()
)