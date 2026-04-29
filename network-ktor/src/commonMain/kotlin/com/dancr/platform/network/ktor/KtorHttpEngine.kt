package com.dancr.platform.network.ktor

import com.dancr.platform.network.client.HttpEngine
import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.security.trust.TrustPolicy
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.Job
import io.ktor.http.HttpMethod as KtorHttpMethod

/**
 * [HttpEngine] implementation backed by a Ktor [HttpClient].
 *
 * Maps SDK [HttpRequest] to Ktor requests and converts responses back to [RawResponse].
 * Supports platform-specific TLS certificate pinning via [createPlatformHttpClient].
 *
 * **Example — creating with certificate pinning:**
 * ```kotlin
 * val engine = KtorHttpEngine.create(
 *     config = NetworkConfig(baseUrl = "https://api.example.com"),
 *     trustPolicy = DefaultTrustPolicy(
 *         pins = mapOf("api.example.com" to setOf(CertificatePin("sha256", "AAAA...=")))
 *     )
 * )
 *
 * val result = engine.execute(HttpRequest(path = "/users", method = HttpMethod.GET))
 * ```
 *
 * @param client The platform-configured Ktor [HttpClient].
 * @see HttpEngine
 * @see KtorErrorClassifier for Ktor-specific error classification.
 */
class KtorHttpEngine(
    private val client: HttpClient
) : HttpEngine {

    override suspend fun execute(request: HttpRequest): RawResponse {
        val response: HttpResponse = client.request(request.path) {
            method = request.method.toKtor()

            request.queryParams.forEach { (key, value) ->
                url.parameters.append(key, value)
            }

            request.headers.forEach { (key, value) ->
                if (!key.equals("content-type", ignoreCase = true)) {
                    headers.append(key, value)
                }
            }

            request.body?.let { bytes ->
                val contentType = request.headers.entries
                    .firstOrNull { (k, _) -> k.equals("content-type", ignoreCase = true) }
                    ?.value
                    ?.let { ContentType.parse(it) }
                    ?: ContentType.Application.OctetStream
                setBody(ByteArrayContent(bytes, contentType))
            }
        }

        return RawResponse(
            statusCode = response.status.value,
            headers = response.headers.toMultiValueMap(),
            body = response.readRawBytes()
        )
    }

    override fun close() {
        client.close()
    }

    override suspend fun healthCheck(): Boolean =
        try {
            !client.engine.coroutineContext[Job]!!.isCancelled
        } catch (_: Exception) {
            false
        }

    companion object {

        /**
         * Creates a [KtorHttpEngine] with the supplied [trustPolicy].
         *
         * **Breaking change (1.0.0):** [trustPolicy] is now non-nullable. Pass
         * [TrustPolicy.SystemDefault] for explicit no-pinning, or use the [createPinned]
         * / [createSystemDefault] factories that document intent at the call site.
         *
         * @param config      Network configuration (base URL, timeouts).
         * @param trustPolicy TLS pinning policy. Use [TrustPolicy.SystemDefault] for no-pinning
         *                    or a [DefaultTrustPolicy] with pins for certificate pinning.
         * @return A configured [KtorHttpEngine] instance.
         */
        fun create(
            config: NetworkConfig,
            trustPolicy: TrustPolicy,
        ): KtorHttpEngine {
            val client = createPlatformHttpClient(config, trustPolicy)
            return KtorHttpEngine(client)
        }

        /**
         * Creates a [KtorHttpEngine] with **certificate pinning** enforced.
         *
         * Use this in production code paths where TLS pinning is required:
         * - Android (OkHttp): pins via `CertificatePinner`.
         * - iOS (Darwin): pins via `handleChallenge` + `SecTrust`.
         *
         * **Example:**
         * ```kotlin
         * val policy = DefaultTrustPolicy(
         *     pins = mapOf(
         *         "api.example.com" to setOf(
         *             CertificatePin("sha256", "AAAA…="),
         *             CertificatePin("sha256", "BBBB…="), // backup pin
         *         ),
         *     ),
         * )
         * val engine = KtorHttpEngine.createPinned(config, policy)
         * ```
         */
        fun createPinned(
            config: NetworkConfig,
            trustPolicy: TrustPolicy,
        ): KtorHttpEngine = create(config, trustPolicy)

        /**
         * Creates a [KtorHttpEngine] using **system-default trust without pinning**.
         *
         * Use this for development environments, or as an explicit acknowledgement that
         * pinning is intentionally not configured. Equivalent to passing
         * [TrustPolicy.SystemDefault] to [create].
         *
         * **Example:**
         * ```kotlin
         * val engine = KtorHttpEngine.createSystemDefault(NetworkConfig(baseUrl = "https://api.dev"))
         * ```
         */
        fun createSystemDefault(
            config: NetworkConfig,
        ): KtorHttpEngine = create(config, TrustPolicy.SystemDefault)
    }
}

private fun HttpMethod.toKtor(): KtorHttpMethod = when (this) {
    HttpMethod.GET -> KtorHttpMethod.Get
    HttpMethod.POST -> KtorHttpMethod.Post
    HttpMethod.PUT -> KtorHttpMethod.Put
    HttpMethod.DELETE -> KtorHttpMethod.Delete
    HttpMethod.PATCH -> KtorHttpMethod.Patch
    HttpMethod.HEAD -> KtorHttpMethod.Head
    HttpMethod.OPTIONS -> KtorHttpMethod.Options
}

private fun Headers.toMultiValueMap(): Map<String, List<String>> =
    names().associateWith { name -> getAll(name).orEmpty() }
