package com.blikeng.chatapp.services

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.administration.*
import com.blikeng.chatapp.dtos.room.RoleAction
import com.blikeng.chatapp.entities.BannedUser
import com.blikeng.chatapp.errors.*
import com.blikeng.chatapp.events.UserBannedEvent
import com.blikeng.chatapp.events.UserRoleChangedEvent
import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserBanRepository
import com.blikeng.chatapp.repositories.UserRepository
import com.blikeng.chatapp.security.UserRole
import com.blikeng.chatapp.security.auth.getId
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class AdministrationService(
    val userRepository: UserRepository,
    val roomRepository: RoomRepository,
    val userBanRepository: UserBanRepository,
    val eventPublisher: ApplicationEventPublisher,
    val userRevocationService: UserRevocationService,
    val meterRegistry: MeterRegistry,
    val redisTemplate: RedisTemplate<String, String>,
    val objectMapper: ObjectMapper,
) {
    private val siteInfoType = object : TypeReference<SiteInfo>() {}
    private val siteInfoTTL = Duration.ofHours(6)

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

    fun getUser(username: String): UserDetailDTO {
        val user = userRepository.findByUsername(username).map { user ->
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

        eventPublisher.publishEvent(UserRoleChangedEvent(
            userId = targetId,
            byUsername = user.username,
            newRole = newRole,
            action = userRoleDTO.action,
        ))
    }

    @Transactional
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

        eventPublisher.publishEvent(UserBannedEvent(
            userId = targetId,
            byUsername = user.username,
            reason = banUserDTO.reason ?: "No reason provided"
        ))
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

        if (banner != null && !checkRequiredRole(banner.role, user.role)) {
            throw InvalidUnbanException()
        }

        userBanRepository.delete(ban)
        userRevocationService.unRevokeBanned(targetId)
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

    fun getSiteInfo(): SiteInfoDTO {
        val counts = getSiteCounts()

        fun gauge(name: String) = meterRegistry.find(name).gauge()?.value() ?: 0.0
        return SiteInfoDTO(
            connectedUsers = gauge("app.users.connected"),
            totalSessions = gauge("app.users.sessions"),
            activeRooms = gauge("app.rooms.active"),
            totalUsers = counts.totalUsers,
            totalRooms = counts.totalRooms,
            bannedUsers = counts.bannedUsers,
        )
    }

    fun getAdvancedSiteInfo(): AdvancedSiteInfoDTO {
        fun gauge(name: String) = meterRegistry.find(name).gauge()?.value() ?: 0.0

        val httpRequests = meterRegistry.find("http.server.requests").timers()
            .groupBy { timer ->
                val tags = timer.id.tags.associate { it.key to it.value }
                (tags["uri"] ?: "unknown") to (tags["method"] ?: "unknown")
            }
            .map { (key, timers) ->
                val (uri, method) = key

                val statuses = timers
                    .groupBy { timer ->
                        val tags = timer.id.tags.associate { it.key to it.value }
                        tags["status"]?.toIntOrNull() ?: 0
                    }
                    .map { (status, groupedTimers) ->
                        HttpStatusCount(
                            status = status,
                            count = groupedTimers.sumOf { it.count() }
                        )
                    }

                val totalCount = timers.sumOf { it.count() }

                val meanMs = if (totalCount > 0) {
                    timers.sumOf {
                        it.mean(TimeUnit.MILLISECONDS) * it.count()
                    } / totalCount
                } else 0.0

                val maxMs = timers.fold(0.0) { acc, timer ->
                    maxOf(acc, timer.max(TimeUnit.MILLISECONDS))
                }

                val errorCount = statuses
                    .filter { it.status >= 500 }
                    .sumOf { it.count }

                val errorRate = if (totalCount > 0) {
                    errorCount.toDouble() / totalCount
                } else 0.0

                HttpEndpointMetric(
                    uri = uri,
                    method = method,
                    statuses = statuses,
                    totalCount = totalCount,
                    errorRate = errorRate,
                    meanMs = meanMs,
                    maxMs = maxMs
                )
            }

        val gcPause = meterRegistry.find("jvm.gc.pause").timer()

        return AdvancedSiteInfoDTO(
            jvmMemoryUsedMb = gauge("jvm.memory.used") / (1024 * 1024),
            jvmMemoryMaxMb = gauge("jvm.memory.max") / (1024 * 1024),
            jvmMemoryCommittedMb = gauge("jvm.memory.committed") / (1024 * 1024),
            jvmThreadsLive = gauge("jvm.threads.live").toInt(),
            jvmThreadsPeak = gauge("jvm.threads.peak").toInt(),
            cpuUsagePercent = gauge("system.cpu.usage") * 100,
            gcPauseMeanMs = gcPause?.mean(TimeUnit.MILLISECONDS) ?: 0.0,
            gcPauseMaxMs = gcPause?.max(TimeUnit.MILLISECONDS) ?: 0.0,
            uptimeSeconds = gauge("process.uptime").toLong(),
            httpRequests = httpRequests
        )
    }

    private fun checkRequiredRole(targetRole: UserRole, userRole: UserRole) : Boolean {
        return userRole.ordinal > targetRole.ordinal
    }

    private fun getSiteCounts(): SiteInfo {
        val key = "site-info:counts"

        val cached = redisTemplate.opsForValue()[key]
        if (cached != null) return objectMapper.readValue(cached, siteInfoType)

        val countUsers = userRepository.count()
        val countRoom = roomRepository.count()
        val countBanned = userBanRepository.count()

        val siteInfo = SiteInfo(
            totalUsers = countUsers,
            totalRooms = countRoom,
            bannedUsers = countBanned
        )

        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(siteInfo), siteInfoTTL)
        return siteInfo
    }

    private data class SiteInfo(
        val totalUsers: Long,
        val totalRooms: Long,
        val bannedUsers: Long,
    )
}