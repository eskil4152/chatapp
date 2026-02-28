package com.blikeng.chatapp.security

import io.github.cdimascio.dotenv.dotenv
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

@Component
class ChatEncrypt {
    private val rng = SecureRandom()
    private val keyV1: SecretKey = loadAesKeyB64("APP_MESSAGE_KEY_V1_B64")

    private fun getSystemVariable(key: String): String? = System.getenv(key)

    private fun getDotenvVariable(key: String): String? {
        val env = dotenv {
            directory = System.getProperty("DOTENV_DIR") ?: "."
            filename = System.getProperty("DOTENV_FILE") ?: ".env"
            ignoreIfMissing = true
        }
        return env[key]
    }

    private fun loadAesKeyB64(keyName: String): SecretKey {
        val b64 = getSystemVariable(keyName) ?: getDotenvVariable(keyName)
        ?: throw RuntimeException("$keyName not found (env or .env)")

        val raw = Base64.getDecoder().decode(b64.trim().trim('"'))
        require(raw.size == 32) { "$keyName must decode to 32 bytes (got ${raw.size})" }
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plaintext: String, aad: ByteArray, keyVersion: Int): Encrypted {
        val nonce = ByteArray(12).also(rng::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyV1, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Encrypted(ciphertext, nonce)
    }

    fun decrypt(ciphertext: ByteArray, nonce: ByteArray, aad: ByteArray, keyVersion: Int): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keyV1, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}

data class Encrypted(val ciphertext: ByteArray, val nonce: ByteArray)

fun aad(roomId: UUID, chatId: UUID, userId: UUID): ByteArray =
    "$roomId|$chatId|$userId".toByteArray(Charsets.UTF_8)