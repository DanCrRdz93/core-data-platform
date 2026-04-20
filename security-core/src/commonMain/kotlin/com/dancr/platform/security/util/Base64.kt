package com.dancr.platform.security.util

/**
 * Pure-Kotlin Base64 encoder (no platform dependencies).
 *
 * Used internally by [CredentialHeaderMapper][com.dancr.platform.security.credential.CredentialHeaderMapper]
 * for HTTP Basic authentication encoding.
 *
 * **Example:**
 * ```kotlin
 * val encoded = Base64.encode("hello".encodeToByteArray()) // "aGVsbG8="
 * val fromStr = Base64.encodeToString("user:pass")          // "dXNlcjpwYXNz"
 * ```
 */
object Base64 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    /**
     * Encodes [bytes] to a Base64 string.
     *
     * @param bytes The raw bytes to encode.
     * @return Base64-encoded string with `=` padding.
     */
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            sb.append(ALPHABET[(b0 shr 2) and 0x3F])
            sb.append(ALPHABET[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            sb.append(if (i + 1 < bytes.size) ALPHABET[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
            sb.append(if (i + 2 < bytes.size) ALPHABET[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }

    /**
     * Convenience: encodes a UTF-8 [value] string to Base64.
     *
     * @param value The string to encode.
     * @return Base64-encoded representation of the UTF-8 bytes.
     */
    fun encodeToString(value: String): String = encode(value.encodeToByteArray())
}
