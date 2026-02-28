package com.blikeng.chatapp.security

import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

class ChatEncrypt(appKeyV1B64: String) {
    private val rng = SecureRandom()
    private val keyV1: SecretKey = run {
        val raw = Base64.decode(appKeyV1B64)
        require(raw.size == 32) { "APP_MESSAGE_KEY_V1_B64 must decode to 32 bytes" }
        SecretKeySpec(raw, "AES")
    }

    fun encrypt(plaintext: String, aad: ByteArray, keyVersion: Int): Encrypted {
        val key = keyFor(keyVersion)
        val nonce = ByteArray(12).also(rng::nextBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Encrypted(ciphertext, nonce)
    }

    fun decrypt(ciphertext: ByteArray, nonce: ByteArray, aad: ByteArray, keyVersion: Int): String {
        val key = keyFor(keyVersion)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)

        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun keyFor(version: Int): SecretKey =
        when (version) {
            1 -> keyV1
            else -> error("Unknown key version: $version")
        }
}

data class Encrypted(val ciphertext: ByteArray, val nonce: ByteArray)

fun aad(roomId: UUID, chatId: UUID, userId: UUID): ByteArray =
    "$roomId|$chatId|$userId".toByteArray(Charsets.UTF_8)