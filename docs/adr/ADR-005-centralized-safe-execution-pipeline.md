# ADR-005: Centralized Safe Execution Pipeline

## Status

**Accepted**

## Context

In a multi-app SDK, every network request must go through a consistent pipeline that ensures:

- Default headers are applied.
- Authentication credentials are injected.
- Retry policies are respected.
- Responses are validated before deserialization.
- Errors are classified into semantic types.
- Observers are notified for metrics and tracing.
- Coroutine cancellation is propagated correctly.
- Exceptions never escape unclassified.

Without centralization, each data source or repository would reimplement parts of this logic, leading to:

- Inconsistent error handling across endpoints.
- Duplicated retry logic with subtle behavioral differences.
- Auth headers applied in some requests but forgotten in others.
- No single point to add observability.
- Testing that requires verifying the same pipeline in every data source.

Two approaches were considered:

1. **Decentralized.** Each `RemoteDataSource` handles its own retry, validation, and error mapping.
2. **Centralized.** A single `SafeRequestExecutor` orchestrates the full lifecycle. Data sources only provide the `HttpRequest` and a `deserialize` function.

## Decision

All network operations flow through `DefaultSafeRequestExecutor`, which implements the `SafeRequestExecutor` interface. Data sources extend `RemoteDataSource`, which delegates to the executor. No data source ever calls `HttpEngine.execute()` directly.

### Pipeline stages (in order)

```
1. PREPARE
   ├── Merge defaultHeaders from NetworkConfig
   ├── Build full URL (baseUrl + path)
   └── Run RequestInterceptor chain (auth, tracing, custom headers)

2. OBSERVE
   └── Notify observers: onRequestStarted

3. RETRY LOOP (governed by RetryPolicy + error.isRetryable)
   │
   ├── 3a. TRANSPORT
   │     └── HttpEngine.execute(request) → RawResponse | Throwable
   │
   ├── 3b. RESPONSE INTERCEPTORS
   │     └── Run ResponseInterceptor chain (logging, caching)
   │
   ├── 3c. OBSERVE
   │     └── Notify observers: onResponseReceived
   │
   ├── 3d. VALIDATE
   │     └── ResponseValidator.validate(response) → Valid | Invalid
   │         • 2xx + Valid → continue
   │         • 2xx + Invalid → ResponseValidation error
   │         • non-2xx → ErrorClassifier.classify() → semantic error
   │
   ├── 3e. DESERIALIZE
   │     └── deserialize(response) → T | Throwable
   │
   └── 3f. RETRY DECISION
         └── If error.isRetryable AND attempts remain → delay → go to 3a
             Notify observers: onRetryScheduled

4. RETURN
   └── NetworkResult.Success(data, metadata) | NetworkResult.Failure(error)
```

### Constructor parameters

```kotlin
class DefaultSafeRequestExecutor(
    engine: HttpEngine,                              // Transport
    config: NetworkConfig,                           // Base URL, timeouts, retry policy
    validator: ResponseValidator = DefaultResponseValidator(),
    classifier: ErrorClassifier = DefaultErrorClassifier(),
    interceptors: List<RequestInterceptor> = emptyList(),
    responseInterceptors: List<ResponseInterceptor> = emptyList(),
    observers: List<NetworkEventObserver> = emptyList()
)
```

All parameters except `engine` and `config` have sensible defaults. A minimal setup requires only two arguments.

### Critical invariant

`CancellationException` is **always rethrown**, never caught, never classified. The executor respects structured concurrency unconditionally. Every `try`/`catch` block in the pipeline explicitly checks for `CancellationException` first.

## Consequences

### Positive

- **Consistency.** Every request — regardless of domain module, endpoint, or team — passes through the same preparation, validation, classification, retry, and observability stages.
- **Single point of extension.** Adding metrics requires passing a `NetworkEventObserver` to the executor. Adding auth requires adding a `RequestInterceptor`. No data source changes needed.
- **Data sources are trivial.** A typical data source is 15–25 lines: build an `HttpRequest`, provide a `deserialize` lambda, call `execute()`. All complexity is in the executor.
- **Retry is transparent.** `ResponseMetadata.attemptCount` tells the consumer how many attempts were needed, without the consumer implementing any retry logic.
- **Testability.** Testing a data source requires mocking only `SafeRequestExecutor` — return a `NetworkResult.Success` or `NetworkResult.Failure`. No HTTP server, no coroutine delay, no retry simulation.

### Negative

- **Single point of failure.** A bug in `DefaultSafeRequestExecutor` affects all requests in all apps. Mitigated by thorough testing (pending) and the `open` nature of `DefaultErrorClassifier` / `DefaultResponseValidator`.
- **No per-request pipeline customization.** All requests share the same interceptor chain and observer list. Per-request behavior is limited to `RequestContext` (operation ID, retry policy override, auth flag). A future enhancement could allow per-request interceptor overrides if needed.
- **Ordering sensitivity.** `RequestInterceptor` and `ResponseInterceptor` lists are ordered. The auth interceptor must run before a logging interceptor that logs final headers. This ordering is implicit — there is no priority mechanism.
