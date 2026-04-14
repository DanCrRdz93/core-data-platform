# ADR-002: Contracts First, Implementation After

## Status

**Accepted**

## Context

When building a shared SDK consumed by multiple applications, the stability of the public API is critical. If consumers depend on concrete classes, any internal refactoring (changing an HTTP library, restructuring retry logic, modifying storage backends) risks breaking all downstream apps.

Two approaches were considered:

1. **Implementation-first:** Build concrete classes (e.g., `KtorRequestExecutor`) and extract interfaces later if needed.
2. **Contracts-first:** Define interfaces and sealed types first. Build implementations behind them. Consumers code to the contract, never to the concrete class.

## Decision

All major components are defined as **interfaces, sealed classes, or abstract classes** before any implementation is written. Consumers depend on these contracts exclusively.

Concrete implementations are:
- Named with a `Default` prefix (`DefaultSafeRequestExecutor`, `DefaultErrorClassifier`, `DefaultResponseValidator`, `DefaultLogSanitizer`, `DefaultTrustPolicy`) to signal they are replaceable defaults.
- Declared as `open class` where extension is expected (e.g., `DefaultErrorClassifier`), enabling platform-specific subclasses without reimplementing the entire contract.
- Injected via constructor parameters, never instantiated internally by other components.

### Contract inventory

| Contract (interface / sealed / abstract) | Default Implementation | Module |
|---|---|---|
| `HttpEngine` | `KtorHttpEngine` | `network-core` / `network-ktor` |
| `SafeRequestExecutor` | `DefaultSafeRequestExecutor` | `network-core` |
| `ErrorClassifier` | `DefaultErrorClassifier` (open) | `network-core` |
| `ResponseValidator` | `DefaultResponseValidator` | `network-core` |
| `RequestInterceptor` | *(consumer-provided)* | `network-core` |
| `ResponseInterceptor` | *(consumer-provided)* | `network-core` |
| `NetworkEventObserver` | `NetworkEventObserver.NOOP` | `network-core` |
| `RemoteDataSource` (abstract class) | *(consumer extends)* | `network-core` |
| `NetworkResult<T>` (sealed class) | — | `network-core` |
| `NetworkError` (sealed class) | — | `network-core` |
| `RetryPolicy` (sealed class) | — | `network-core` |
| `SecretStore` | `AndroidSecretStore`, `IosSecretStore` | `security-core` |
| `CredentialProvider` | *(consumer-provided)* | `security-core` |
| `SessionController` | *(pending)* | `security-core` |
| `TrustPolicy` | `DefaultTrustPolicy` (open) | `security-core` |
| `LogSanitizer` | `DefaultLogSanitizer` | `security-core` |
| `Credential` (sealed interface) | — | `security-core` |
| `SecurityError` (sealed class) | — | `security-core` |

## Consequences

### Positive

- **Stable public API.** Consumers code to `SafeRequestExecutor`, not `DefaultSafeRequestExecutor`. Internal refactoring (e.g., changing the retry algorithm) does not break any consumer.
- **Testability.** Every dependency is mockable. Testing a `UserRemoteDataSource` requires only a mock `SafeRequestExecutor` — no HTTP server, no Ktor, no coroutine complexity.
- **Pluggability.** Replacing Ktor with OkHttp means implementing `HttpEngine` in a new module. Zero changes to `network-core`, `security-core`, or any domain module.
- **Gradual implementation.** Contracts can be designed and reviewed before writing any implementation. `SessionController` is a defined interface today; its implementation can follow when `SecretStore` is ready.

### Negative

- **More files.** Each major component has at least two files: the contract and the default implementation. This is accepted as the cost of maintainability at scale.
- **Indirection.** Developers must navigate from interface to implementation when debugging. Mitigated by consistent `Default*` naming and clear package structure.
- **Sealed types require source modification to extend.** Adding a new `NetworkError` subtype or `RetryPolicy` variant requires modifying the sealed class file. This is intentional — it forces all consumers to handle new cases at compile time.
