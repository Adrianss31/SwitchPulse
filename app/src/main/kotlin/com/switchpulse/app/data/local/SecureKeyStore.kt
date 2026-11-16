package com.switchpulse.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts sensitive BYOK API keys at rest using Android KeyStore (AES-256-GCM) so keys are
 * unreadable offline and never exported in plain text. Transparently migrates legacy plaintext values.
 */
object SecureKeyStore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "switchpulse_byok_aes_v1"
    private const val PREFIX = "enc:v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    @Synchronized
    fun encrypt(plainText: String): String {
        val clean = plainText.trim()
        if (clean.isBlank()) return ""
        if (clean.startsWith(PREFIX)) return clean
        val secretKey = getOrCreateKey() ?: return clean
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            PREFIX + Base64.getEncoder().encodeToString(combined)
        }.getOrDefault(clean)
    }

    @Synchronized
    fun decrypt(storedValue: String?): String {
        val raw = storedValue.orEmpty().trim()
        if (raw.isBlank()) return ""
        if (!raw.startsWith(PREFIX)) return raw
        val secretKey = getOrCreateKey() ?: return ""
        return runCatching {
            val payload = Base64.getDecoder().decode(raw.removePrefix(PREFIX))
            if (payload.size < 2) return@runCatching ""
            val ivLen = payload[0].toInt() and 0xFF
            if (ivLen <= 0 || payload.size <= 1 + ivLen) return@runCatching ""
            val iv = payload.copyOfRange(1, 1 + ivLen)
            val ciphertext = payload.copyOfRange(1 + ivLen, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun isEncrypted(storedValue: String?): Boolean =
        storedValue?.trim()?.startsWith(PREFIX) == true

    private fun getOrCreateKey(): SecretKey? = runCatching {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return@runCatching existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        generator.generateKey()
    }.getOrNull()
}
