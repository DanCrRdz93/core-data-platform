# ADR-003: No Transport Details in the Public API

## Status

**Accepted**

## Context

The SDK uses [Ktor 3.0.3](https://ktor.io/) as its HTTP transport via the `:network-ktor` module. Ktor provides types like `HttpClient`, `HttpResponse`, `HttpRequestBuilder`, `HttpRequestTimeoutException`, and engine-specific classes (`OkHttpConfig`, `DarwinClientEngineConfig`).

If domain modules (`:sample-api`, future `:payments-api`, etc.) or consuming applications import Ktor types directly, several problems arise:

- **Vendor lock-in.** Replacing Ktor with OkHttp, URLSession, or a custom engine would require changes in every consumer.
- **Leaky abstractions.** Consumers would need to understand Ktor's configuration DSL, engine selection, and exception hierarchy.
- **Testing friction.** Mocking Ktor types is significantly more complex than mocking a simple `HttpEngine` interface.
- **Binary compatibility risk.** A Ktor major version upgrade would be a breaking change for all consumers.

## Decision

No module outside `:network-ktor` may import any `io.ktor.*` type. The public API surface uses exclusively SDK-defined types:

| Consumer sees | Ktor equivalent (hidden) |
|---|---|
| `HttpEngine` | `HttpClient` |
| `HttpRequest` | `HttpRequestBuilder` |
| `RawResponse` | `HttpResponse` |
| `HttpMethod` (enum) | `io.ktor.http.HttpMethod` |
| `NetworkError.Timeout` | `HttpRequestTimeoutException` |
| `NetworkConfig` (timeouts) | `HttpTimeout` plugin config |
| `SafeRequestExecutor` | *(no Ktor equivalent)* |

The translation boundary lives in `KtorHttpEngine`:

```
SDK types                        Ktor types
──────────                       ──────────
HttpRequest ──── KtorHttpEngine ──── HttpRequestBuilder
RawResponse ◀─── KtorHttpEngine ◀─── HttpResponse
HttpMethod  ──── toKtor()       ──── io.ktor.http.HttpMethod
```

`KtorErrorClassifier` translates `HttpRequestTimeoutException` into `NetworkError.Timeout` so that no Ktor exception type escapes the transport module.

### Verification rule

A simple grep can verify this invariant at any time:

```bash
# Must return zero results for all modules except network-ktor
grep -r "io.ktor" network-core/ security-core/ sample-api/
```

## Consequences

### Positive

- **Transport is replaceable.** Creating `:network-okhttp` requires only implementing `HttpEngine` and `ErrorClassifier`. No consumer code changes.
- **Simplified testing.** Domain modules test against `SafeRequestExecutor` (one `suspend fun` returning `NetworkResult<T>`). No `MockEngine`, no Ktor plugin mocking.
- **Ktor upgrades are isolated.** A Ktor 3→4 migration affects exactly one module (`:network-ktor`). All other modules are untouched.
- **Consistent API surface.** Consumers interact with the same types regardless of which HTTP library is used underneath.

### Negative

- **Translation overhead.** Every request is translated from `HttpRequest` → Ktor builder, and every response from Ktor `HttpResponse` → `RawResponse`. This involves copying headers and reading the body as `ByteArray`. The cost is negligible for typical API payloads but would matter for very large responses (>10 MB), where streaming would be needed.
- **Feature gap.** Ktor features that have no SDK equivalent (WebSockets, SSE, multipart uploads) are not accessible through the current abstraction. These require extending `HttpEngine` or defining parallel contracts.
- **Double configuration.** Timeout values flow from `NetworkConfig` → `KtorHttpEngine.create()` → Ktor `HttpTimeout` plugin. There is no way to configure Ktor-specific features (e.g., connection pool size) through `NetworkConfig` today.
