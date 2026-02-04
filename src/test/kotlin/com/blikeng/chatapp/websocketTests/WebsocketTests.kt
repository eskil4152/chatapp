package com.blikeng.chatapp.websocketTests

import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.websocket.ChatWebSocketHandler
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class WebsocketTests {
    @MockK
    lateinit var chatService: ChatService

    @MockK
    lateinit var session: WebSocketSession

    private lateinit var handler: ChatWebSocketHandler

    @BeforeEach
    fun setup() {
        handler = ChatWebSocketHandler(chatService)
    }

    @Test
    fun connectionEstablishedShouldRegisterSession() {
        val userId = UUID.randomUUID()
        val username = "u"
        val attributes: MutableMap<String, Any> = mutableMapOf(
            "userId" to userId,
            "username" to username
        )

        every { chatService.registerSession(any(), any()) } returns Unit
        every { session.attributes } returns attributes

        handler.afterConnectionEstablished(session)
        verify { chatService.registerSession(userId, session) }

        verify(exactly = 1) { chatService.registerSession(any(), any()) }
    }

    @Test
    fun connectionEstablishedShouldFail() {
        val username = "u"
        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to username
        )

        every { session.attributes } returns attributes

        val exception = assertFailsWith<ResponseStatusException> {
            handler.afterConnectionEstablished(session)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, exception.statusCode)
        assertEquals("No userID found", exception.reason)

        verify(exactly = 0) { chatService.registerSession(any(), any()) }
    }

    @Test
    fun connectionClosedShouldUnregisterSession() {
        val userId = UUID.randomUUID()
        val username = "u"
        val attributes: MutableMap<String, Any> = mutableMapOf(
            "userId" to userId,
            "username" to username
        )

        every { chatService.registerSession(any(), any()) } returns Unit
        every { chatService.removeSession(any(), any()) } returns Unit
        every { session.attributes } returns attributes

        handler.afterConnectionEstablished(session)
        handler.afterConnectionClosed(session, CloseStatus.NORMAL)

        verify(exactly = 1) { chatService.registerSession(any(), any()) }
        verify(exactly = 1) { chatService.removeSession(any(), any()) }
    }

    @Test
    fun connectionClosedShouldFail() {
        val username = "u"
        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to username
        )

        every { session.attributes } returns attributes

        val ex = assertFailsWith<ResponseStatusException> {
            handler.afterConnectionClosed(session, CloseStatus.NORMAL)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("No userID found", ex.reason)

        verify(exactly = 0) { chatService.removeSession(any(), any()) }
    }

    @Test
    fun shouldSendJoinMessage(){
        val payload = TextMessage((jacksonObjectMapper().createObjectNode()
            .put("type", "JOIN")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { chatService.joinRoom(any(), any()) } returns Unit
        every { chatService.broadcast(any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify (exactly = 1) { chatService.joinRoom(any(), any()) }
        verify (exactly = 1) { chatService.broadcast(any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendMessage(){
        val payload = TextMessage((jacksonObjectMapper().createObjectNode()
            .put("type", "MESSAGE")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { chatService.broadcast(any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 1) { chatService.broadcast(any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendLeaveMessage(){
        val payload = TextMessage((jacksonObjectMapper().createObjectNode()
            .put("type", "LEAVE")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { chatService.leaveRoom(any(), any()) } returns Unit
        every { chatService.broadcast(any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 1) { chatService.broadcast(any(), any()) }
        verify (exactly = 1) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldFailToSendMessageWithoutUsername(){
        val payload = TextMessage((jacksonObjectMapper().createObjectNode()
            .put("type", "JOIN")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes

        val ex = assertFailsWith<ResponseStatusException> {
            handler.handleMessage(session, payload)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("No username found", ex.reason)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 0) { chatService.broadcast(any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldFailToSendMessageWithoutUserId(){
        val payload = TextMessage((jacksonObjectMapper().createObjectNode()
            .put("type", "JOIN")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u"
        )

        every { session.attributes } returns attributes

        val ex = assertFailsWith<ResponseStatusException> {
            handler.handleMessage(session, payload)
        }

        assertEquals(HttpStatus.UNAUTHORIZED, ex.statusCode)
        assertEquals("No User ID found", ex.reason)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 0) { chatService.broadcast(any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldFailToSendMessageWithInvalidType(){
        val payload = TextMessage((jacksonObjectMapper().createObjectNode()
            .put("type", "NONE")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes

        val exec = assertFailsWith<ResponseStatusException> {
            handler.handleMessage(session, payload)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exec.statusCode)
        assertEquals(exec.reason, "Invalid message type")

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 0) { chatService.broadcast(any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }
}