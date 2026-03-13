package com.blikeng.chatapp.security.crypto

@Suppress("ArrayInDataClass")
data class Encrypted(val ciphertext: ByteArray, val nonce: ByteArray)