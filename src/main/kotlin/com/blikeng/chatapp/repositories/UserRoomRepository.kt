package com.blikeng.chatapp.repositories

import com.blikeng.chatapp.entities.RoomEntity
import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.entities.UserRoomEntity
import com.blikeng.chatapp.entities.UserRoomId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRoomRepository: JpaRepository<UserRoomEntity, UserRoomId> {
    fun save(userRoom: UserRoomEntity): UserRoomEntity

    //TODO: Maybe fix
    @Query("SELECT ur.room FROM UserRoomEntity ur WHERE ur.user.id = :userId")
    fun findAllRoomsByUserId(@Param("userId") userId: UUID): List<RoomEntity>
}