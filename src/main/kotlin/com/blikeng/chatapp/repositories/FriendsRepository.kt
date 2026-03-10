package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import com.blikeng.chatapp.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FriendsRepository: JpaRepository<FriendsEntity, FriendsId> {
    @Query("""
        SELECT 
            CASE WHEN f.userA.id = :userId THEN f.userB ELSE f.userA END 
        FROM FriendsEntity f 
        WHERE f.userA.id = :userId OR f.userB.id = :userId
    """)
    fun findFriendsForUser(@Param("userId") userId: UUID): List<UserEntity>
}