package com.blikeng.chatapp.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
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

    //val friends: MutableList<UserEntity>,

    @ManyToMany
    @JoinTable(
        name = "user_rooms",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "room_id")]
    )
    val rooms: MutableSet<RoomEntity> = mutableSetOf()
)