package com.blikeng.chatapp.controllers

import com.blikeng.chatapp.dtos.UserIdDTO
import com.blikeng.chatapp.dtos.administration.AdvancedSiteInfoDTO
import com.blikeng.chatapp.dtos.administration.BanUserDTO
import com.blikeng.chatapp.dtos.administration.BannedUserDTO
import com.blikeng.chatapp.dtos.administration.ElevatedUserDTO
import com.blikeng.chatapp.dtos.administration.SiteInfoDTO
import com.blikeng.chatapp.dtos.administration.UserDetailDTO
import com.blikeng.chatapp.dtos.administration.UserRoleDTO
import com.blikeng.chatapp.services.AdministrationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class AdministrationController(
    private val administrationService: AdministrationService,
) {
    @GetMapping("/users")
    fun getElevatedUsers(): ResponseEntity<List<ElevatedUserDTO>> = ResponseEntity.ok(administrationService.getElevatedUsers())

    @GetMapping("/user/{username}")
    fun getUser(
        @PathVariable username: String,
    ): ResponseEntity<UserDetailDTO> = ResponseEntity.ok(administrationService.getUser(username))

    @PostMapping("/change-user-role")
    fun changeUserRole(
        @RequestBody userRoleDTO: UserRoleDTO,
    ): ResponseEntity<String> {
        administrationService.changeUserRole(userRoleDTO)

        return ResponseEntity.ok("Role updated successfully")
    }

    @PostMapping("/ban-user")
    fun banUser(
        @RequestBody banUserDTO: BanUserDTO,
    ): ResponseEntity<String> {
        administrationService.banUser(banUserDTO)

        return ResponseEntity.ok("Banned user")
    }

    @PostMapping("/unban-user")
    fun unbanUser(
        @RequestBody userIdDTO: UserIdDTO,
    ): ResponseEntity<String> {
        administrationService.unbanUser(userIdDTO)

        return ResponseEntity.ok("Unbanned user")
    }

    @GetMapping("/banned")
    fun getBannedUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<List<BannedUserDTO>> = ResponseEntity.ok(administrationService.getAllUserBans(page, size))

    @GetMapping("/site-info")
    fun getSiteInfo(): ResponseEntity<SiteInfoDTO> = ResponseEntity.ok(administrationService.getSiteInfo())

    @GetMapping("/advanced-site-info")
    fun getAdvancedSiteInfo(): ResponseEntity<AdvancedSiteInfoDTO> = ResponseEntity.ok(administrationService.getAdvancedSiteInfo())
}
