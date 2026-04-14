package com.dancr.platform.network

import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.observability.LoggingObserver
import com.dancr.platform.network.observability.NetworkLogger
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// OWASP MASVS-aligned security guardrail tests for network-core.
class NetworkSecurityGuardrailsTest {

    // -- NetworkConfig HTTPS enforcement (MASVS-NETWORK-1) --

    @Test
    fun networkConfig_rejectsPlaintextHttp() {
        assertFails("Should reject http:// URLs") {
            NetworkConfig(baseUrl = "http://api.example.com")
        }
    }

    @Test
    fun networkConfig_acceptsHttps() {
        val config = NetworkConfig(baseUrl = "https://api.example.com")
        assertTrue(config.baseUrl.startsWith("https://"))
    }

    @Test
    fun networkConfig_allowsInsecureWhenExplicitlyOptedIn() {
        val config = NetworkConfig(
            baseUrl = "http://localhost:8080",
            allowInsecureConnections = true
        )
        assertTrue(config.baseUrl.startsWith("http://"))
    }

    @Test
    fun networkConfig_rejectsBlankUrl() {
        assertFails("Should reject blank URL") {
            NetworkConfig(baseUrl = "   ")
        }
    }

    // -- HttpRequest.toString() redaction (MASVS-STORAGE / MASVS-PRIVACY) --

    @Test
    fun httpRequestToString_doesNotExposeHeaderValues() {
        val request = HttpRequest(
            path = "/users",
            method = HttpMethod.GET,
            headers = mapOf("Authorization" to "Bearer eyJhbGciOiJSUzI1NiIs")
        )
        val str = request.toString()
        assertFalse(str.contains("eyJhbGciOiJSUzI1NiIs"), "HttpRequest.toString() must not expose header values")
        assertTrue(str.contains("Authorization"), "HttpRequest.toString() should show header keys")
    }

    @Test
    fun httpRequestToString_doesNotExposeQueryParamValues() {
        val request = HttpRequest(
            path = "/users",
            method = HttpMethod.GET,
            queryParams = mapOf("token" to "secret123")
        )
        val str = request.toString()
        assertFalse(str.contains("secret123"), "HttpRequest.toString() must not expose query param values")
        assertTrue(str.contains("token"), "HttpRequest.toString() should show query param keys")
    }

    // -- RawResponse.toString() redaction --

    @Test
    fun rawResponseToString_doesNotExposeHeaderValues() {
        val response = RawResponse(
            statusCode = 200,
            headers = mapOf("Set-Cookie" to listOf("session=abc123; Secure; HttpOnly")),
            body = "secret body".encodeToByteArray()
        )
        val str = response.toString()
        assertFalse(str.contains("abc123"), "RawResponse.toString() must not expose header values")
        assertFalse(str.contains("secret body"), "RawResponse.toString() must not expose body content")
        assertTrue(str.contains("Set-Cookie"), "RawResponse.toString() should show header keys")
    }

    // -- LoggingObserver default sanitizer (MASVS-PRIVACY) --

    @Test
    fun loggingObserver_defaultSanitizer_redactsAllHeaders() {
        val logged = mutableListOf<String>()
        val logger = NetworkLogger { _, _, message -> logged.add(message) }

        val observer = LoggingObserver(logger = logger) // no explicit sanitizer

        val request = HttpRequest(
            path = "/test",
            method = HttpMethod.POST,
            headers = mapOf("Authorization" to "Bearer secret_token_xyz")
        )
        observer.onRequestStarted(request, null)

        assertTrue(logged.isNotEmpty(), "Should have logged something")
        val logLine = logged.first()
        assertFalse(logLine.contains("secret_token_xyz"), "Default sanitizer must redact header values")
        assertTrue(logLine.contains("██"), "Default sanitizer must use redaction placeholder")
    }
}
