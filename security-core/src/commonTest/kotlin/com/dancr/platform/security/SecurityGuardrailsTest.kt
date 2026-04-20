package com.dancr.platform.security

import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.session.SessionCredentials
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OWASP MASVS-aligned security guardrail tests.
 *
 * Verifies that sensitive data is **never** exposed through `toString()`,
 * preventing accidental leaks in logs, crash reports, and debuggers.
 *
 * Covers:
 * - [Credential] subtypes (`Bearer`, `ApiKey`, `Basic`, `Custom`)
 * - [SessionCredentials] (access + refresh tokens)
 */
class SecurityGuardrailsTest {

    // -- Credential.toString() redaction (MASVS-STORAGE / MASVS-PRIVACY) --

    @Test
    fun bearerToString_doesNotExposeToken() {
        val token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.secret"
        val credential = Credential.Bearer(token)
        val str = credential.toString()
        assertFalse(str.contains(token), "Bearer.toString() must not expose the token")
        assertTrue(str.contains("██"), "Bearer.toString() must show redaction placeholder")
    }

    @Test
    fun apiKeyToString_doesNotExposeKey() {
        val key = "sk-live-abc123xyz789"
        val credential = Credential.ApiKey(key, "X-API-Key")
        val str = credential.toString()
        assertFalse(str.contains(key), "ApiKey.toString() must not expose the key")
        assertTrue(str.contains("██"), "ApiKey.toString() must show redaction placeholder")
        assertTrue(str.contains("X-API-Key"), "ApiKey.toString() should show header name (non-sensitive)")
    }

    @Test
    fun basicToString_doesNotExposeUsernameOrPassword() {
        val credential = Credential.Basic("admin", "s3cret!")
        val str = credential.toString()
        assertFalse(str.contains("admin"), "Basic.toString() must not expose username")
        assertFalse(str.contains("s3cret!"), "Basic.toString() must not expose password")
        assertTrue(str.contains("██"), "Basic.toString() must show redaction placeholder")
    }

    @Test
    fun customToString_doesNotExposePropertyValues() {
        val credential = Credential.Custom("oauth", mapOf("token" to "secret123", "scope" to "read"))
        val str = credential.toString()
        assertFalse(str.contains("secret123"), "Custom.toString() must not expose property values")
        assertTrue(str.contains("token"), "Custom.toString() should show property keys")
        assertTrue(str.contains("scope"), "Custom.toString() should show property keys")
    }

    // -- SessionCredentials.toString() redaction --

    @Test
    fun sessionCredentialsToString_doesNotExposeRefreshToken() {
        val creds = SessionCredentials(
            credential = Credential.Bearer("access_tok"),
            refreshToken = "rt_secret_refresh_abc"
        )
        val str = creds.toString()
        assertFalse(str.contains("access_tok"), "SessionCredentials.toString() must not expose credential token")
        assertFalse(str.contains("rt_secret_refresh_abc"), "SessionCredentials.toString() must not expose refresh token")
    }

    @Test
    fun sessionCredentialsToString_showsNullRefreshToken() {
        val creds = SessionCredentials(credential = Credential.Bearer("tok"))
        val str = creds.toString()
        assertTrue(str.contains("refreshToken=null"), "Should show null when no refresh token")
    }
}
