package com.dancr.platform.security.credential

/**
 * Represents an authentication credential used to authorize network requests.
 *
 * Each variant maps to a standard HTTP authentication scheme. The [toString]
 * override on every variant redacts sensitive values to prevent accidental
 * leakage in logs (OWASP MASVS-STORAGE-2).
 *
 * **Example — creating and inspecting credentials:**
 * ```kotlin
 * val bearer = Credential.Bearer(token = "eyJhbGciOiJIUzI1NiIs...")
 * val apiKey  = Credential.ApiKey(key = "sk-live-abc123", headerName = "X-API-Key")
 * val basic   = Credential.Basic(username = "admin", password = "s3cret")
 * val custom  = Credential.Custom(
 *     type = "OAuth1",
 *     properties = mapOf("oauth_token" to "xyz", "oauth_signature" to "abc")
 * )
 *
 * println(bearer) // Bearer(token=██)
 * println(apiKey)  // ApiKey(headerName=X-API-Key, key=██)
 * ```
 *
 * @see CredentialHeaderMapper for converting a [Credential] into HTTP headers.
 * @see CredentialProvider   for supplying the active credential to interceptors.
 */
sealed interface Credential {

    /**
     * OAuth 2.0 / JWT Bearer token.
     *
     * Produces the header `Authorization: Bearer <token>`.
     *
     * ```kotlin
     * val cred = Credential.Bearer(token = "eyJhbGciOiJIUzI1NiIs...")
     * ```
     *
     * @property token The raw bearer token string.
     */
    data class Bearer(val token: String) : Credential {
        override fun toString(): String = "Bearer(token=██)"
    }

    /**
     * API-key credential sent in a custom header.
     *
     * ```kotlin
     * val cred = Credential.ApiKey(key = "sk-live-abc123")
     * // Uses default header "X-API-Key"
     *
     * val custom = Credential.ApiKey(key = "my-key", headerName = "Authorization")
     * ```
     *
     * @property key        The API key value.
     * @property headerName HTTP header name used to transmit the key (default `X-API-Key`).
     */
    data class ApiKey(val key: String, val headerName: String = "X-API-Key") : Credential {
        override fun toString(): String = "ApiKey(headerName=$headerName, key=██)"
    }

    /**
     * HTTP Basic Authentication credential.
     *
     * Produces the header `Authorization: Basic <base64(username:password)>`.
     *
     * ```kotlin
     * val cred = Credential.Basic(username = "admin", password = "s3cret")
     * ```
     *
     * @property username The username.
     * @property password The password.
     */
    data class Basic(val username: String, val password: String) : Credential {
        override fun toString(): String = "Basic(username=██, password=██)"
    }

    /**
     * Freeform credential for non-standard authentication schemes (e.g. OAuth 1.0, HMAC).
     *
     * Each entry in [properties] is injected as an HTTP header by
     * [CredentialHeaderMapper].
     *
     * ```kotlin
     * val cred = Credential.Custom(
     *     type = "HMAC",
     *     properties = mapOf(
     *         "X-Auth-Signature" to "sha256=abc...",
     *         "X-Auth-Timestamp" to "1713600000"
     *     )
     * )
     * ```
     *
     * @property type       Descriptive label (for logging/diagnostics only).
     * @property properties Map of header-name → header-value pairs.
     */
    data class Custom(val type: String, val properties: Map<String, String>) : Credential {
        override fun toString(): String = "Custom(type=$type, properties=[${properties.keys.joinToString()}])"
    }
}
