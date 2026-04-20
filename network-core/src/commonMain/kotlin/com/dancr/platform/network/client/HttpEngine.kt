package com.dancr.platform.network.client

/**
 * Core transport abstraction for executing HTTP requests.
 *
 * Implement per HTTP library (Ktor, OkHttp, URLSession). The engine **must not**
 * throw for HTTP error status codes — return them as [RawResponse]. Only throw for
 * transport-level failures (connectivity, timeout, TLS).
 *
 * **Example — using the Ktor implementation:**
 * ```kotlin
 * val engine: HttpEngine = KtorHttpEngine.create(
 *     config = NetworkConfig(baseUrl = "https://api.example.com"),
 *     trustPolicy = myTrustPolicy
 * )
 *
 * val response = engine.execute(
 *     HttpRequest(path = "/users", method = HttpMethod.GET)
 * )
 * println(response.statusCode) // 200
 *
 * engine.close()
 * ```
 *
 * @see KtorHttpEngine for the built-in Ktor implementation.
 * @see SafeRequestExecutor for the managed execution layer on top of this engine.
 */
interface HttpEngine {

    /**
     * Executes an [HttpRequest] and returns the raw HTTP response.
     *
     * @param request The fully-formed HTTP request (absolute URL, headers, body).
     * @return [RawResponse] containing status code, headers, and body bytes.
     * @throws Exception Only for transport-level failures (connectivity, timeout, TLS).
     */
    suspend fun execute(request: HttpRequest): RawResponse

    /** Releases underlying resources (connection pool, HTTP client). */
    fun close()

    /**
     * Lightweight liveness probe for the underlying connection pool / HTTP client.
     *
     * @return `true` if the engine is operational and can likely execute requests.
     *         Default: always returns `true`. Override for engines with connection pool management.
     */
    suspend fun healthCheck(): Boolean = true
}
