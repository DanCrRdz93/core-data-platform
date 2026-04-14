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

        // Creates a KtorHttpEngine with platform-appropriate TLS configuration.
        // When trustPolicy is non-null, certificate pinning is enforced:
        //  - Android (OkHttp): via CertificatePinner
        //  - iOS (Darwin): via handleChallenge with SecTrust evaluation
        // When trustPolicy is null, system default trust is used (no pinning).
        fun create(
            config: NetworkConfig,
            trustPolicy: TrustPolicy? = null
        ): KtorHttpEngine {
            val client = createPlatformHttpClient(config, trustPolicy)
            return KtorHttpEngine(client)
        }
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
