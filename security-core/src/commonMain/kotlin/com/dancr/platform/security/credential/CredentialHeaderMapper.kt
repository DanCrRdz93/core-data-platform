package com.dancr.platform.security.credential

import com.dancr.platform.security.util.Base64

/**
 * Converts a [Credential] into the corresponding HTTP header(s).
 *
 * **Example:**
 * ```kotlin
 * val headers = CredentialHeaderMapper.toHeaders(
 *     Credential.Bearer(token = "eyJhbGciOiJIUzI1NiIs...")
 * )
 * // headers = {"Authorization" to "Bearer eyJhbGciOiJIUzI1NiIs..."}
 *
 * val apiHeaders = CredentialHeaderMapper.toHeaders(
 *     Credential.ApiKey(key = "sk-live-abc", headerName = "X-API-Key")
 * )
 * // apiHeaders = {"X-API-Key" to "sk-live-abc"}
 * ```
 *
 * @see Credential for the supported credential variants.
 */
object CredentialHeaderMapper {

    /**
     * Maps [credential] to a set of HTTP headers ready to be attached to a request.
     *
     * @param credential The active credential to convert.
     * @return A map of header-name to header-value pairs.
     */
    fun toHeaders(credential: Credential): Map<String, String> = when (credential) {
        is Credential.Bearer ->
            mapOf("Authorization" to "Bearer ${credential.token}")
        is Credential.ApiKey ->
            mapOf(credential.headerName to credential.key)
        is Credential.Basic -> {
            val encoded = Base64.encodeToString("${credential.username}:${credential.password}")
            mapOf("Authorization" to "Basic $encoded")
        }
        is Credential.Custom ->
            credential.properties
    }
}
