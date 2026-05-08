package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.FriendsEntity
import com.blikeng.chatapp.entities.FriendsId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FriendsRepository : JpaRepository<FriendsEntity, FriendsId> {
    @Query(
        """
        select f
        from FriendsEntity f
        join fetch f.userA
        join fetch f.userB
        where f.userA.id = :userId or f.userB.id = :userId
    """,
    )
    fun findFriendsForUser(
        @Param("userId") userId: UUID,
    ): List<FriendsEntity>
}
