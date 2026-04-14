# ADR-006: Centralized Error Classification

## Status

**Accepted**

## Context

HTTP operations can fail in many ways: transport failures (DNS resolution, TCP timeout, TLS handshake), HTTP error responses (401, 403, 404, 500), deserialization exceptions, and unexpected runtime errors. Each failure type requires different handling by consumers:

- **Connectivity** → show "no internet" banner, queue for offline retry.
- **Timeout** → retry with backoff.
- **401 Authentication** → redirect to login or trigger token refresh.
- **403 Authorization** → show "access denied" message.
- **500 Server Error** → retry automatically, show generic error.
- **Serialization** → log diagnostic, show generic error (API contract mismatch).

Without centralized classification:

- Each data source interprets raw HTTP status codes differently.
- Platform-specific exceptions (`java.net.SocketTimeoutException`, `NSURLErrorTimedOut`) leak into business logic.
- Adding a new error category (e.g., rate limiting on 429) requires changes in every data source.
- Retry decisions are scattered and inconsistent.

## Decision

All errors are classified at a **single point** — the `ErrorClassifier` interface — and expressed as `NetworkError` sealed class subtypes. No raw exception or HTTP status code escapes the execution pipeline.

### Classification architecture

```
Throwable / RawResponse
        │
        ▼
  ErrorClassifier.classify(response?, cause?)
        │
        ├── cause != null → classifyThrowable(cause)
        │     │
        │     ├── Class name contains "Timeout"      → NetworkError.Timeout
        │     ├── Class name contains "UnknownHost"   → NetworkError.Connectivity
        │     ├── Class name contains "ConnectException" → NetworkError.Connectivity
        │     ├── Class name contains "Serializ"      → NetworkError.Serialization
        │     └── else                                → NetworkError.Unknown
        │
        └── response != null → classifyResponse(response)
              │
              ├── 401 → NetworkError.Authentication
              ├── 403 → NetworkError.Authorization
              ├── 4xx → NetworkError.ClientError(statusCode)
              ├── 5xx → NetworkError.ServerError(statusCode)
              └── else → NetworkError.Unknown
```

### Two-layer classifier design

1. **`DefaultErrorClassifier`** (in `:network-core`, `open class`) — Uses cross-platform heuristics. In `commonMain`, platform exception types are not available, so classification uses `cause::class.simpleName` pattern matching. This is a reasonable default that works for most exceptions.

2. **`KtorErrorClassifier`** (in `:network-ktor`, extends `DefaultErrorClassifier`) — Overrides `classifyThrowable()` to add **type-safe** matching for Ktor exceptions (e.g., `HttpRequestTimeoutException`). Falls through to the parent for all non-Ktor exceptions.

This pattern is designed for extension:

```kotlin
// Future: platform-aware classifier
class AndroidErrorClassifier : DefaultErrorClassifier() {
    override fun classifyThrowable(cause: Throwable) = when (cause) {
        is java.net.SocketTimeoutException -> NetworkError.Timeout(...)
        is java.net.UnknownHostException -> NetworkError.Connectivity(...)
        is javax.net.ssl.SSLHandshakeException -> NetworkError.Unknown(...)  // or a future TLS error
        else -> super.classifyThrowable(cause)
    }
}
```

### Retryability is a property of the error

Each `NetworkError` subtype declares `open val isRetryable`:

| Error | `isRetryable` |
|---|---|
| `Connectivity` | `true` |
| `Timeout` | `true` |
| `ServerError` | `true` |
| `Cancelled` | `false` |
| `Authentication` | `false` |
| `Authorization` | `false` |
| `ClientError` | `false` |
| `Serialization` | `false` |
| `ResponseValidation` | `false` |
| `Unknown` | `false` |

The executor reads `error.isRetryable` — it does not hardcode which error types to retry. This means a custom `ErrorClassifier` can return errors with different retryability characteristics without modifying the executor.

### Two-audience error model

Every `NetworkError` carries:

- **`message: String`** — Human-readable, safe for end users. Never contains stack traces, status codes, or technical jargon. Example: *"Unable to reach the server"*.
- **`diagnostic: Diagnostic?`** — Internal debugging data. Contains `description` (technical detail), `cause` (original `Throwable`), and `metadata` (key-value pairs like `statusCode`). Never shown to users.

## Consequences

### Positive

- **Exhaustive handling.** `NetworkError` is sealed. Consumers use `when (error)` and the compiler verifies all branches. Adding a new error type is a compile-time breaking change — no silent misses.
- **Consistent retry behavior.** Retryability is declared on the error, not decided by each data source. The pipeline applies it uniformly.
- **Clean consumer code.** Consumers handle `NetworkError.Authentication` — not `response.statusCode == 401`. The error model encodes HTTP semantics once, at classification time.
- **Transport-agnostic.** Consumers never see `HttpRequestTimeoutException` or `SocketTimeoutException`. They see `NetworkError.Timeout`. Changing from Ktor to OkHttp changes which classifier runs, but the error types consumers handle remain identical.
- **Extensible.** Adding 429 Rate Limiting requires: (1) add `NetworkError.RateLimited` to the sealed class, (2) handle 429 in `classifyResponse()`. All consumers get a compile error until they handle the new case.

### Negative

- **Heuristic classification in `commonMain`.** Class name matching (`simpleName.contains("Timeout")`) is fragile. An exception named `CustomTimeoutHandler` would be misclassified. This is mitigated by platform classifiers (`KtorErrorClassifier`) that match type-safely and only fall through to heuristics for unknown exceptions.
- **Sealed class modification required for new errors.** Adding `NetworkError.RateLimited` requires editing `NetworkError.kt` and releasing a new version of `:network-core`. This is intentional — it ensures compile-time safety — but it means error evolution is a coordinated SDK change, not a local consumer decision.
- **No error composition.** A single `NetworkError` cannot represent multiple simultaneous issues. This has not been needed in practice, but complex scenarios (e.g., "timeout during retry after authentication failure") only surface the final error.
