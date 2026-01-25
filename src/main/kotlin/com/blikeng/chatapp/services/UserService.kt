package com.blikeng.chatapp.services

import com.blikeng.chatapp.entities.UserEntity
import com.blikeng.chatapp.repositories.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(@Autowired private val userRepository: UserRepository) {
    fun getUser(username: String): UserEntity? {
        val user = userRepository.findByUsername(username)

        if (user == null) return null

        return user
    }

    fun getUserById(id: UUID): UserEntity? {
        return userRepository.findById(id).orElse(null)
    }


}