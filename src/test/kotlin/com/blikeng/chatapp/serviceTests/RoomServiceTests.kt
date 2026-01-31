package com.blikeng.chatapp.serviceTests

import com.blikeng.chatapp.repositories.RoomRepository
import com.blikeng.chatapp.repositories.UserRoomRepository
import com.blikeng.chatapp.security.JwtService
import com.blikeng.chatapp.services.AuthService
import com.blikeng.chatapp.services.RoomService
import com.blikeng.chatapp.services.UserService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test

@ExtendWith(MockKExtension::class)
class RoomServiceTests {
    @MockK private lateinit var roomRepository: RoomRepository
    @MockK private lateinit var userService: UserService
    @MockK private lateinit var jwtService: JwtService
    @MockK private lateinit var userRoomRepository: UserRoomRepository

    @InjectMockKs
    lateinit var roomService: RoomService

    @Test
    fun shouldMakeNewRoom(){

    }

    @Test
    fun shouldFailToMakeRoomWithInvalidCredentials(){

    }

    @Test
    fun shouldJoinRoom(){

    }

    @Test
    fun shouldFailToJoinRoomWithInvalidCredentials(){

    }

    @Test
    fun shouldLeaveRoom(){

    }

    @Test
    fun shouldFailToLeaveRoomWithInvalidCredentials(){

    }
}