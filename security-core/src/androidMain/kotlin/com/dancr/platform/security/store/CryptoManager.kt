package com.dancr.platform.security.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class CryptoManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        // Format: [ivLength (1 byte)] [iv] [ciphertext]
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        val ivLength = encryptedData[0].toInt()
        val iv = encryptedData.copyOfRange(1, 1 + ivLength)
        val ciphertext = encryptedData.copyOfRange(1 + ivLength, encryptedData.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(plaintext: String): ByteArray =
        encrypt(plaintext.toByteArray(Charsets.UTF_8))

    fun decryptString(encryptedData: ByteArray): String =
        String(decrypt(encryptedData), Charsets.UTF_8)

    private fun getOrCreateKey(): SecretKey {
        val entry = keyStore.getEntry(keyAlias, null)
        if (entry is KeyStore.SecretKeyEntry) {
            return entry.secretKey
        }
        return createKey()
    }

    private fun createKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DEFAULT_KEY_ALIAS = "_dancr_crypto_key_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val AES_KEY_SIZE_BITS = 256
    }
}
