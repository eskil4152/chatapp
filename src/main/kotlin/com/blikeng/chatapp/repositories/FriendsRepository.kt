package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FriendsRepository: JpaRepository<FriendsEntity, FriendsId> {
    fun findFriendsForUser(@Param("userId") userId: UUID): List<UserEntity>
}