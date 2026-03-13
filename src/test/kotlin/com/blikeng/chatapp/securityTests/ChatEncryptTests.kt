package com.blikeng.chatapp.securityTests

import com.blikeng.chatapp.config.configureAad
import com.blikeng.chatapp.security.ChatEncrypt
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

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
        val keyVersion = 1

        val roomId = UUID.randomUUID()
        val chatId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val aad = configureAad(roomId, chatId, userId)

        val encrypted = encrypt.encrypt(
            plaintext = stringToEncrypt,
            aad = aad,
            keyVersion = keyVersion
        )

        val decrypted = encrypt.decrypt(
            ciphertext = encrypted.ciphertext,
            nonce = encrypted.nonce,
            aad = aad,
            keyVersion = keyVersion
        )

        assertEquals(decrypted, stringToEncrypt)
    }

    @Test
    fun shouldFailDecryptWithEmptyNonce() {
        val plaintext = "x"
        val keyVersion = 1
        val aad = configureAad(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        val encrypted = encrypt.encrypt(plaintext, aad, keyVersion)

        assertFails {
            encrypt.decrypt(encrypted.ciphertext, byteArrayOf(), aad, keyVersion)
        }
    }

    @Test
    fun shouldFailDecryptWithWrongAad() {
        val plaintext = "x"
        val keyVersion = 1

        val aad1 = configureAad(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        val aad2 = configureAad(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

        val encrypted = encrypt.encrypt(plaintext, aad1, keyVersion)

        assertFails {
            encrypt.decrypt(encrypted.ciphertext, encrypted.nonce, aad2, keyVersion)
        }
    }

    @Test
    fun shouldThrowOnInvalidBase64() {
        val keyB64 = "not base64"

        assertFailsWith<IllegalArgumentException> {
            ChatEncrypt(keyB64)
        }
    }

    @Test
    fun shouldThrowWhenDecodedKeyIsNot32Bytes() {
        val raw16 = ByteArray(16) { 2 }
        val b64 = Base64.getEncoder().encodeToString(raw16)

        assertFailsWith<IllegalArgumentException> {
            ChatEncrypt(b64)
        }
    }
}