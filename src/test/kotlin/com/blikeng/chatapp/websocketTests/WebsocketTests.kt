package com.blikeng.chatapp.websocketTests

import com.blikeng.chatapp.services.ChatService
import com.blikeng.chatapp.websocket.ChatWebSocketHandler
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import junit.framework.TestCase.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExtendWith(MockKExtension::class)
class WebsocketTests {
    @MockK
    private lateinit var chatService: ChatService

    @MockK
    private lateinit var session: WebSocketSession

    @MockK
    private lateinit var objectMapper: ObjectMapper

    @InjectMockKs
    private lateinit var handler: ChatWebSocketHandler

    @BeforeEach
    fun setup() {
        handler = ChatWebSocketHandler(chatService, objectMapper)
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
        val payload = TextMessage((objectMapper.createObjectNode()
            .put("type", "JOIN")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { chatService.joinRoom(any(), any()) } returns Unit
        every { chatService.broadcast(any(), any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify (exactly = 1) { chatService.joinRoom(any(), any()) }
        verify (exactly = 1) { chatService.broadcast(any(), any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendMessage(){
        val payload = TextMessage((objectMapper.createObjectNode()
            .put("type", "MESSAGE")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { chatService.broadcast(any(), any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 1) { chatService.broadcast(any(), any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendLeaveMessage(){
        val payload = TextMessage((objectMapper.createObjectNode()
            .put("type", "LEAVE")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { chatService.leaveRoom(any(), any()) } returns Unit
        every { chatService.broadcast(any(), any(), any()) } returns Unit

        handler.handleMessage(session, payload)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 1) { chatService.broadcast(any(), any(), any()) }
        verify (exactly = 1) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendErrorWithoutUsername() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "JOIN")
                .put("roomId", UUID.randomUUID().toString())
                .toString()
        )

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        val msgSlot = io.mockk.slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 1) { session.sendMessage(any()) }

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(401, json["code"].asInt())
        assertEquals("No username found", json["message"].asText())

        verify(exactly = 0) { chatService.joinRoom(any(), any()) }
        verify(exactly = 0) { chatService.broadcast(any(), any(), any()) }
        verify(exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldFailToSendMessageWithoutUserId(){
        val payload = TextMessage((objectMapper.createObjectNode()
            .put("type", "JOIN")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u"
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        val msgSlot = io.mockk.slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(401, json["code"].asInt())
        assertEquals("No User ID found", json["message"].asText())

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 0) { chatService.broadcast(any(), any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldFailToSendMessageWithInvalidType(){
        val payload = TextMessage((objectMapper.createObjectNode()
            .put("type", "")
            .put("message", "m" )
            .put("roomId", UUID.randomUUID().toString())).toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        val msgSlot = io.mockk.slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Invalid message type", json["message"].asText())

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 0) { chatService.broadcast(any(), any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldReceivePing(){
        val payload = TextMessage((objectMapper.createObjectNode()
            .put("type", "PING")
            .put("message", "" ))
            .toString())

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes

        handler.handleMessage(session, payload)

        verify (exactly = 0) { chatService.joinRoom(any(), any()) }
        verify (exactly = 0) { chatService.broadcast(any(), any(), any()) }
        verify (exactly = 0) { chatService.leaveRoom(any(), any()) }
    }

    @Test
    fun shouldSendErrorForResponseStatusException() {
        val roomId = UUID.randomUUID().toString()

        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "JOIN")
                .put("roomId", roomId)
                .toString()
        )

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        every { chatService.joinRoom(any(), any()) } throws ResponseStatusException(HttpStatus.FORBIDDEN, "Not permitted")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 1) { session.sendMessage(any()) }

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(403, json["code"].asInt())
        assertEquals("Not permitted", json["message"].asText())

        verify(exactly = 0) { chatService.broadcast(any(), any(), any()) }
    }

    @Test
    fun shouldSendErrorForIllegalArgumentException() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "MESSAGE")
                .put("roomId", UUID.randomUUID().toString())
                .put("message", "m")
                .toString()
        )

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        every { chatService.broadcast(any(), any(), any()) } throws IllegalArgumentException("Bad request X")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 1) { session.sendMessage(any()) }

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Bad request X", json["message"].asText())
    }

    @Test
    fun shouldSendErrorForUnknownException() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "MESSAGE")
                .put("roomId", UUID.randomUUID().toString())
                .put("message", "m")
                .toString()
        )

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        every { chatService.broadcast(any(), any(), any()) } throws RuntimeException("exception")

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 1) { session.sendMessage(any()) }

        val json = objectMapper.readTree(msgSlot.captured.payload)
        assertEquals("ERROR", json["type"].asText())
        assertEquals(500, json["code"].asInt())
        assertEquals("Internal error", json["message"].asText())
    }

    @Test
    fun shouldNotSendErrorWhenSessionClosed() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "MESSAGE")
                .put("roomId", UUID.randomUUID().toString())
                .put("message", "m")
                .toString()
        )

        val attributes: MutableMap<String, Any> = mutableMapOf(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns false

        every { chatService.broadcast(any(), any(), any()) } throws RuntimeException("exception")

        every { session.sendMessage(any()) } just Runs

        handler.handleMessage(session, payload)

        verify(exactly = 0) { session.sendMessage(any()) }
    }

    @Test
    fun shouldUseExceptionMessageWhenReasonIsNull() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "JOIN")
                .put("roomId", UUID.randomUUID().toString())
                .toString()
        )

        val attributes = mutableMapOf<String, Any>(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        every { chatService.joinRoom(any(), any()) } throws
                ResponseStatusException(HttpStatus.BAD_REQUEST)

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)

        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertTrue(json["message"].asText().contains("400 BAD_REQUEST"))
    }

    @Test
    fun shouldFallbackToRequestFailedWhenReasonAndMessageNull() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "JOIN")
                .put("roomId", UUID.randomUUID().toString())
                .toString()
        )

        val attributes = mutableMapOf<String, Any>(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        val ex = ResponseStatusException(HttpStatus.BAD_REQUEST)
        every { chatService.joinRoom(any(), any()) } throws ex

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)

        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
    }

    @Test
    fun shouldFallbackToBadRequestWhenIllegalArgumentMessageNull() {
        val payload = TextMessage(
            objectMapper.createObjectNode()
                .put("type", "MESSAGE")
                .put("message", "m")
                .put("roomId", UUID.randomUUID().toString())
                .toString()
        )

        val attributes = mutableMapOf<String, Any>(
            "username" to "u",
            "userId" to UUID.randomUUID()
        )

        every { session.attributes } returns attributes
        every { session.isOpen } returns true

        every { chatService.broadcast(any(), any(), any()) } throws IllegalArgumentException()

        val msgSlot = slot<TextMessage>()
        every { session.sendMessage(capture(msgSlot)) } just Runs

        handler.handleMessage(session, payload)

        val json = objectMapper.readTree(msgSlot.captured.payload)

        assertEquals("ERROR", json["type"].asText())
        assertEquals(400, json["code"].asInt())
        assertEquals("Bad request", json["message"].asText())
    }
}