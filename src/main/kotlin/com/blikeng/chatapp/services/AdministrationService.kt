package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.administration.BanUserDTO
import com.blikeng.chatapp.dtos.administration.BannedUserDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDTO
import com.blikeng.chatapp.dtos.administration.UserDetailDTO
import com.blikeng.chatapp.dtos.administration.UserRoleDTO
import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.errors.AlreadyBannedException
import com.blikeng.chatapp.errors.InvalidParametersException
import com.blikeng.chatapp.errors.InvalidUUIDException
import com.blikeng.chatapp.errors.InvalidUnbanException
import com.blikeng.chatapp.errors.InvalidUserException
import com.blikeng.chatapp.errors.NotBannedException
import com.blikeng.chatapp.errors.NotPermittedException
import com.blikeng.chatapp.errors.UserNotFoundException
import com.blikeng.chatapp.repositories.UserBanRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.UserRole
import com.blikeng.chatapp.security.auth.getId
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AdministrationService(
    val userRepository: UserRepository,
    val userBanRepository: UserBanRepository
) {
    fun getElevatedUsers(): List<ElevatedUserDTO> {
        return userRepository.findAllByRoleNot(UserRole.USER).map { user ->
            ElevatedUserDTO(
                id = user.id,
                username = user.username,
                avatarUrl = user.avatarUrl,
                role = user.role,
                createdAt = user.createdAt,
            )
        }
    }

    fun getUser(userId: String): UserDetailDTO {
        val id = try {
            UUID.fromString(userId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val user = userRepository.findById(id).map { user ->
            UserDetailDTO(
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
        }.orElseThrow { UserNotFoundException() }

        return user
    }

    @Transactional
    fun changeUserRole(userRoleDTO: UserRoleDTO) {
        val user = userRepository.findById(getId()).orElseThrow { InvalidUserException() }
        val targetId = try {
            UUID.fromString(userRoleDTO.id)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val target = userRepository.findById(targetId).orElseThrow { UserNotFoundException() }

        if (!checkRequiredRole(target.role, user.role)){
            throw NotPermittedException()
        }

        val entries = UserRole.entries
        val newRole = when (userRoleDTO.action) {
            RoleAction.PROMOTE -> entries[target.role.ordinal + 1]
            RoleAction.DEMOTE  -> entries[target.role.ordinal - 1]
        }

        target.role = newRole

        userRepository.save(target)

        // TODO: send notification to target
    }

    fun banUser(banUserDTO: BanUserDTO) {
        val user = userRepository.findById(getId()).orElseThrow { InvalidUserException() }
        val targetId = try {
            UUID.fromString(banUserDTO.id)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val target = userRepository.findById(targetId).orElseThrow { UserNotFoundException() }

        if (!checkRequiredRole(target.role, user.role)){
            throw NotPermittedException()
        }

        if (userBanRepository.existsById(targetId)) throw AlreadyBannedException()
        val ban = BannedUser(
            userId = targetId,
            bannedBy = user.id,
            reason = banUserDTO.reason
        )

        userBanRepository.save(ban)
    }

    fun unbanUser(userIdDTO: UserIdDTO) {
        val user = userRepository.findById(getId()).orElseThrow { InvalidUserException() }
        val targetId = try {
            UUID.fromString(userIdDTO.userId)
        } catch (_: IllegalArgumentException) {
            throw InvalidUUIDException()
        }

        val ban = userBanRepository.findById(targetId).orElseThrow { NotBannedException() }
        val banner = userRepository.findById(ban.bannedBy).orElse(null)

        if (banner != null) {
            if (!checkRequiredRole(banner.role, user.role)) throw InvalidUnbanException()
        }

        userBanRepository.delete(ban)
    }

    fun getAllUserBans(page: Int, size: Int): List<BannedUserDTO> {
        if (page < 0 || size !in setOf(25, 50, 100)) throw InvalidParametersException()

        return userBanRepository.findAllWithUsers(PageRequest.of(page, size)).map { ban ->
            BannedUserDTO(
                userId = ban.userId,
                username = ban.username,
                bannedBy = ban.bannedBy,
                bannedByUsername = ban.bannedByUsername,
                bannedByRole = ban.bannedByRole,
                bannedAt = ban.bannedAt,
                reason = ban.reason
            )
        }.content
    }

    private fun checkRequiredRole(targetRole: UserRole, userRole: UserRole) : Boolean {
        return userRole.ordinal > targetRole.ordinal
    }
}