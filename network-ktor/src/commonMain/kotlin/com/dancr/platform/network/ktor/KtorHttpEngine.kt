package com.dancr.platform.network.ktor

import com.dancr.platform.network.client.HttpEngine
import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.config.NetworkConfig
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.content.ByteArrayContent
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

    companion object {

        fun create(config: NetworkConfig): KtorHttpEngine {
            val client = HttpClient {
                install(HttpTimeout) {
                    connectTimeoutMillis = config.connectTimeout.inWholeMilliseconds
                    requestTimeoutMillis = config.readTimeout.inWholeMilliseconds
                    socketTimeoutMillis = config.writeTimeout.inWholeMilliseconds
                }

                // TODO: Certificate pinning integration.
                //  Accept TrustPolicy from security-core and configure platform TLS:
                //  - Android (OkHttp): CertificatePinner via engine { config { certificatePinner(...) } }
                //  - iOS (Darwin): SecTrust evaluation via handleChallenge in NSURLSessionDelegate
                //  This requires platform-specific source sets (androidMain / iosMain) in this module.

                // TODO: Logging integration.
                //  Install Ktor's Logging plugin wired to a LogSanitizer-aware logger:
                //  install(Logging) { logger = SanitizedKtorLogger(logSanitizer) }

                expectSuccess = false
            }
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
