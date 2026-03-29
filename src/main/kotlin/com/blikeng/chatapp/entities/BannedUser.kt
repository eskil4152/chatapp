package com.blikeng.chatapp.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "banned_user")
class BannedUser (
    @EmbeddedId
    val id: BannedUserId
)

@Embeddable
class BannedUserId (
    val userId:UUID,
    val roomId: UUID
) : Serializable
