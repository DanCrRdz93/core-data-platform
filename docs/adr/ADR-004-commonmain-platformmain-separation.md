# ADR-004: commonMain / platformMain Separation Strategy

## Status

**Accepted**

## Context

Kotlin Multiplatform (KMP) projects must decide how to distribute code across source sets:

- **`commonMain`** — shared across all targets. Can only use Kotlin stdlib, `kotlinx` libraries, and other KMP modules.
- **`androidMain`** — has access to the Android SDK (`android.content.Context`, `android.security.keystore`, `androidx.security`).
- **`iosMain`** — has access to Apple frameworks via cinterop (`platform.Security`, `platform.Foundation`).

The distribution strategy directly impacts:
- How much code is shared vs. duplicated.
- How testable the shared logic is.
- How easy it is to add a new platform target (e.g., JVM backend, watchOS).

## Decision

**Maximize `commonMain`. Push platform-specific code to the absolute edges.**

The rule is:

| Code type | Source set | Examples |
|---|---|---|
| Interfaces and contracts | `commonMain` | `HttpEngine`, `SecretStore`, `SessionController`, `TrustPolicy` |
| Sealed classes and data models | `commonMain` | `NetworkError`, `Credential`, `SessionState`, `RetryPolicy` |
| Default implementations (pure logic) | `commonMain` | `DefaultSafeRequestExecutor`, `DefaultErrorClassifier`, `DefaultLogSanitizer` |
| Execution pipeline and retry logic | `commonMain` | `DefaultSafeRequestExecutor` |
| Configuration classes | `commonMain` | `NetworkConfig`, `SecurityConfig`, `AndroidStoreConfig`, `KeychainConfig` |
| Platform I/O (storage, keychain) | `androidMain` / `iosMain` | `AndroidSecretStore`, `IosSecretStore` |

**Current distribution:**

| Module | `commonMain` | `androidMain` | `iosMain` |
|---|---|---|---|
| `:network-core` | 20 files | 0 files | 0 files |
| `:network-ktor` | 2 files | 0 files | 0 files |
| `:security-core` | 15 files | 2 files | 2 files |
| `:sample-api` | 6 files | 0 files | 0 files |
| **Total** | **43 files** | **2 files** | **2 files** |

95%+ of the codebase is in `commonMain`.

### Platform source set rules

1. **Only implement interfaces defined in `commonMain`.** Platform code never defines new public contracts.
2. **Configuration data classes are in `commonMain`**, even if they reference platform concepts. `AndroidStoreConfig` (preferences name, master key alias) is a plain data class with no Android imports. `KeychainConfig` (service name, accessibility level) is a plain data class with no iOS imports.
3. **Platform enums are in platform source sets** only when they map to platform constants. `KeychainAccessibility` is in `iosMain` because its values correspond to `kSecAttrAccessible*` constants.
4. **Ktor engine selection uses Gradle, not source sets.** `:network-ktor` has no `androidMain` or `iosMain` Kotlin code. Platform engine selection (`ktor-client-okhttp` vs. `ktor-client-darwin`) is declared in `build.gradle.kts` dependencies.

## Consequences

### Positive

- **Maximum code sharing.** Business logic, error classification, retry policies, interceptor chains, result types — all shared. No per-platform reimplementation.
- **Single test surface.** Tests for `DefaultSafeRequestExecutor`, `DefaultErrorClassifier`, `NetworkResult`, and all pure logic run in `commonTest` — once, for all platforms.
- **Easy target addition.** Adding JVM (for backend) or watchOS would require only new `SecretStore` implementations in their respective source sets. All contracts and pipeline logic come for free.
- **Clear boundary.** When a developer asks *"should this be in commonMain?"*, the answer is almost always yes. The only exception is direct platform API calls.

### Negative

- **Configuration classes in `commonMain` can be misleading.** `AndroidStoreConfig` is a `commonMain` data class that is only meaningful on Android. It compiles on iOS but has no use there. This is accepted because it keeps the type visible for documentation and factory signatures.
- **No `expect`/`actual` usage.** The project currently uses interfaces + platform implementations instead of `expect`/`actual` declarations. This is intentional — `expect`/`actual` creates compilation coupling between source sets, while interfaces allow implementation injection and easier testing. `expect`/`actual` may be introduced later for true platform primitives (e.g., `Dispatchers.IO` if needed in `commonMain`).
- **Platform source sets are thin.** `androidMain` and `iosMain` have 2 files each. This asymmetry is by design but may surprise developers who expect more platform code in a KMP project.
