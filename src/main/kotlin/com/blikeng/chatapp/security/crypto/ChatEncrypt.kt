package com.blikeng.chatapp.security.crypto

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ==========================
// Encrypts and decrypts chat message content using AES-GCM.
// Uses additional authenticated data (AAD) to bind ciphertext
// to a specific room, message, and sender.
// ==========================
@Component
class ChatEncrypt(
    @Value("\${app.crypto.messageKeyV1B64}")
    keyB64: String
) {
    private val rng = SecureRandom()
    private val keyV1: SecretKey = run {
        val raw = Base64.getDecoder().decode(keyB64.trim())
        require(raw.size == 32)
        SecretKeySpec(raw, "AES")
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

