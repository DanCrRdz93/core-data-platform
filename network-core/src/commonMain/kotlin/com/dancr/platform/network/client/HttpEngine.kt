package com.dancr.platform.network.client

// Core transport abstraction. Implement per HTTP library (Ktor, OkHttp, URLSession).
// The engine MUST NOT throw for HTTP error status codes — return them as RawResponse.
// Only throw for transport-level failures (connectivity, timeout, TLS).
// TODO: Add suspend fun healthCheck(): Boolean for connection pool / liveness probing.
interface HttpEngine {

    suspend fun execute(request: HttpRequest): RawResponse

    fun close()
}
