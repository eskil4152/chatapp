package com.blikeng.chatapp.securityTests.crypto

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.security.crypto.ChatEncrypt
import java.util.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows

class ChatEncryptTests {
    // ==========================
    // Encryption tests. Verifies:
    // - Encryption and decryption of messages.
    // - Fail conditions: Empty nonce, wrong AAD, invalid base64, invalid key.
    // ==========================
    private var encrypt = ChatEncrypt("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")

    @Test
    fun shouldEncryptAndDecrypt(){
        val stringToEncrypt = "Hello from unit testing!"

        val roomId = UUID.randomUUID()
        val chatId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val aad = configureAad(roomId, chatId, userId)

        val encrypted = encrypt.encrypt(
            plaintext = stringToEncrypt,
            aad = aad,
        )

        val decrypted = encrypt.decrypt(
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            aad = aad,
        )

        assertEquals(decrypted, stringToEncrypt)
    }

    @Test
    fun shouldFailDecryptWithEmptyNonce() {
        val plaintext = "x"
        val aad = configureAad(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        val encrypted = encrypt.encrypt(plaintext, aad)

        assertThrows<Exception> {
            encrypt.decrypt(encrypted.ciphertext, byteArrayOf(), aad)
        }
    }

    @Test
    fun shouldFailDecryptWithWrongAad() {
        val plaintext = "x"

        val aad1 = configureAad(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val aad2 = configureAad(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        val encrypted = encrypt.encrypt(plaintext, aad1)

        assertThrows<Exception> {
            encrypt.decrypt(encrypted.ciphertext, encrypted.nonce, aad2)
        }
    }

    @Test
    fun shouldThrowOnInvalidBase64() {
        val keyB64 = "not base64"

        assertThrows<IllegalArgumentException> {
            ChatEncrypt(keyB64)
        }
    }

    @Test
    fun shouldThrowWhenDecodedKeyIsNot32Bytes() {
        val raw16 = ByteArray(16) { 2 }
        val b64 = Base64.getEncoder().encodeToString(raw16)

        assertThrows<IllegalArgumentException> {
            ChatEncrypt(b64)
        }
    }
}