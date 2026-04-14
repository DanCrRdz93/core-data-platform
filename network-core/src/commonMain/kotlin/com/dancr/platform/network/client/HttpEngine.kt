package com.dancr.platform.network.client

// Core transport abstraction. Implement per HTTP library (Ktor, OkHttp, URLSession).
// The engine MUST NOT throw for HTTP error status codes — return them as RawResponse.
// Only throw for transport-level failures (connectivity, timeout, TLS).
interface HttpEngine {

    suspend fun execute(request: HttpRequest): RawResponse

    fun close()

    // Lightweight liveness probe for the underlying connection pool / HTTP client.
    // Returns true if the engine is operational and can likely execute requests.
    // Default: always returns true. Override for engines with connection pool management.
    suspend fun healthCheck(): Boolean = true
}
