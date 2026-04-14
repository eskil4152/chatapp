package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDetailDTO
import com.blikeng.chatapp.errors.InvalidUUIDException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.auth.getId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdministrationService(
    val userRepository: UserRepository
) {
    fun getElevatedUsers(): List<ElevatedUserDTO> {
        userRepository.findById(getId()).orElseThrow { InvalidUserException() }

        return userRepository.findAllUsersWhereUserRoleIsNotUser().map { user ->
            ElevatedUserDTO(
                id = user.id,
                username = user.username,
                avatarUrl = user.avatarUrl,
                role = user.role,
                createdAt = user.createdAt,
            )
        }
    }

    fun getUser(userIdDTO: UserIdDTO): ElevatedUserDetailDTO {
        userRepository.findById(getId()).orElseThrow { InvalidUserException() }

        val id = try {
            UUID.fromString(userIdDTO.userId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val user = userRepository.findById(id).orElseThrow { UserNotFoundException() }

        val elevatedDetail = ElevatedUserDetailDTO(
            id = user.id,
            username = user.username,
            bio = user.bio,
            email = user.email,
            fullName = user.fullName,
            avatarUrl = user.avatarUrl,
            role = user.role,
            createdAt = user.createdAt,
            rooms = null
        )

        return elevatedDetail
    }

    @Transactional
    fun changeUserRole() {
        userRepository.findById(getId()).orElseThrow { InvalidUserException() }
    }

    fun banUser() {
        userRepository.findById(getId()).orElseThrow { InvalidUserException() }
    }

    fun unbanUser() {
        userRepository.findById(getId()).orElseThrow { InvalidUserException() }
    }
}