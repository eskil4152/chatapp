package com.blikeng.chatapp.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl

@Configuration
class RoleConfig {
    @Bean
    fun roleHierarchy(): RoleHierarchy =
        RoleHierarchyImpl
            .withDefaultRolePrefix()
            .role("SUPERUSER")
            .implies("ADMIN")
            .role("ADMIN")
            .implies("MODERATOR")
            .role("MODERATOR")
            .implies("TRUSTED")
            .role("TRUSTED")
            .implies("USER")
            .build()
}
