package com.dancr.platform.network.execution

import com.dancr.platform.network.client.HttpEngine
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.client.RawResponse
import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.config.RetryPolicy
import com.dancr.platform.network.observability.NetworkEventObserver
import com.dancr.platform.network.result.Diagnostic
import com.dancr.platform.network.result.NetworkError
import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.network.result.ResponseMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.time.TimeSource

class DefaultSafeRequestExecutor(
    private val engine: HttpEngine,
    private val config: NetworkConfig,
    private val validator: ResponseValidator = DefaultResponseValidator(),
    private val classifier: ErrorClassifier = DefaultErrorClassifier(),
    private val interceptors: List<RequestInterceptor> = emptyList(),
    private val responseInterceptors: List<ResponseInterceptor> = emptyList(),
    private val observers: List<NetworkEventObserver> = emptyList()
) : SafeRequestExecutor {

    override suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext?,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> {
        return try {
            val prepared = prepareRequest(request, context)
            val policy = context?.retryPolicyOverride ?: config.retryPolicy
            observers.forEach { it.onRequestStarted(prepared, context) }
            executeWithRetry(prepared, context, policy, deserialize)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val error = classifier.classify(null, e)
            NetworkResult.Failure(error)
        }
    }

    // -- Request preparation --

    private suspend fun prepareRequest(
        request: HttpRequest,
        context: RequestContext?
    ): HttpRequest {
        val merged = request.copy(
            path = buildUrl(config.baseUrl, request.path),
            headers = config.defaultHeaders + request.headers
        )
        return interceptors.fold(merged) { current, interceptor ->
            interceptor.intercept(current, context)
        }
    }

    private fun buildUrl(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        val relative = path.trimStart('/')
        return if (relative.isEmpty()) base else "$base/$relative"
    }

    // -- Retry loop --

    private suspend fun <T> executeWithRetry(
        request: HttpRequest,
        context: RequestContext?,
        policy: RetryPolicy,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> {
        val maxAttempts = policy.maxAttempts()
        var lastFailure: NetworkResult.Failure? = null

        repeat(maxAttempts) { attempt ->
            val result = executeSingle(request, context, attempt + 1, deserialize)

            when {
                result.isSuccess -> return result
                result is NetworkResult.Failure -> {
                    lastFailure = result
                    val shouldRetry = attempt < maxAttempts - 1 && result.error.isRetryable
                    if (shouldRetry) {
                        val delayMs = computeDelay(policy, attempt)
                        observers.forEach {
                            it.onRetryScheduled(request, attempt + 1, maxAttempts, result.error, delayMs)
                        }
                        delay(delayMs)
                    } else {
                        return result
                    }
                }
            }
        }

        return lastFailure ?: NetworkResult.Failure(NetworkError.Unknown())
    }

    private fun computeDelay(policy: RetryPolicy, attempt: Int): Long = when (policy) {
        is RetryPolicy.None -> 0L
        is RetryPolicy.FixedDelay -> policy.delay.inWholeMilliseconds
        is RetryPolicy.ExponentialBackoff -> {
            val calculated = policy.initialDelay.inWholeMilliseconds *
                policy.multiplier.pow(attempt.toDouble())
            minOf(calculated.toLong(), policy.maxDelay.inWholeMilliseconds)
        }
    }

    private fun RetryPolicy.maxAttempts(): Int = when (this) {
        is RetryPolicy.None -> 1
        is RetryPolicy.FixedDelay -> maxRetries + 1
        is RetryPolicy.ExponentialBackoff -> maxRetries + 1
    }

    // -- Single execution step --

    private suspend fun <T> executeSingle(
        request: HttpRequest,
        context: RequestContext?,
        attemptNumber: Int,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> {
        val mark = TimeSource.Monotonic.markNow()

        // Step 1: Transport
        val rawResponse: RawResponse
        try {
            rawResponse = engine.execute(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val error = classifier.classify(null, e)
            val durationMs = mark.elapsedNow().inWholeMilliseconds
            observers.forEach { it.onRequestFailed(request, error, durationMs, context) }
            return NetworkResult.Failure(error)
        }

        val durationMs = mark.elapsedNow().inWholeMilliseconds

        // Step 2: Response interceptors
        val response = responseInterceptors.fold(rawResponse) { current, interceptor ->
            interceptor.intercept(current, request, context)
        }

        observers.forEach { it.onResponseReceived(request, response, durationMs, context) }

        // Step 3: Validation
        when (val outcome = validator.validate(response)) {
            is ValidationOutcome.Valid -> { /* continue to deserialization */ }
            is ValidationOutcome.Invalid -> {
                val error = if (response.isSuccessful) {
                    // 2xx but validator rejected — domain-level validation failure
                    NetworkError.ResponseValidation(
                        reason = outcome.reason,
                        diagnostic = Diagnostic(
                            description = "Validation failed on ${response.statusCode}: ${outcome.reason}"
                        )
                    )
                } else {
                    // Non-2xx — delegate to classifier for semantic mapping
                    classifier.classify(response, null)
                }
                observers.forEach { it.onRequestFailed(request, error, durationMs, context) }
                return NetworkResult.Failure(error)
            }
        }

        // Step 4: Deserialization
        return try {
            val data = deserialize(response)
            val metadata = ResponseMetadata(
                statusCode = response.statusCode,
                headers = response.headers,
                durationMs = durationMs,
                attemptCount = attemptNumber
            )
            NetworkResult.Success(data, metadata)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val error = NetworkError.Serialization(
                diagnostic = Diagnostic(
                    description = "Deserialization failed: ${e.message}",
                    cause = e
                )
            )
            observers.forEach { it.onRequestFailed(request, error, durationMs, context) }
            NetworkResult.Failure(error)
        }
    }
}
