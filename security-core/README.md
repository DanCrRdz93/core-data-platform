# :security-core

**Security Abstractions for Kotlin Multiplatform**

This module defines the complete contract surface for credential management, session lifecycle, secure storage, TLS trust evaluation, and log sanitization — all as platform-agnostic abstractions with platform-specific implementations at the edges.

---

## Purpose

`:security-core` answers one question:

> *"How do I manage credentials, sessions, secrets, and trust policies across Android and iOS — without coupling any business logic to platform-specific security APIs?"*

This module has **zero dependency on `:network-core`**. It knows nothing about HTTP, Ktor, or request execution. The integration point between networking and security is `CredentialHeaderMapper`, which converts a `Credential` into a plain `Map<String, String>` — no network types involved.

---

## Responsibilities

| Responsibility | Owner |
|---|---|
| Model authentication credentials | `Credential` (sealed interface) |
| Supply the active credential for requests | `CredentialProvider` |
| Convert credentials to HTTP-safe headers | `CredentialHeaderMapper` |
| Manage session lifecycle reactively | `SessionController`, `SessionState`, `SessionEvent` |
| Store secrets securely per platform | `SecretStore`, `AndroidSecretStore`, `IosSecretStore` |
| Evaluate host trust and certificate pinning | `TrustPolicy`, `TrustEvaluation`, `CertificatePin` |
| Redact sensitive data in logs | `LogSanitizer`, `DefaultLogSanitizer` |
| Configure security policies | `SecurityConfig` |
| Model security errors semantically | `SecurityError`, `Diagnostic` |
| Provide cross-platform Base64 encoding | `Base64` utility |

---

## Principal Contracts

### Credential

```kotlin
sealed interface Credential {
    data class Bearer(val token: String)
    data class ApiKey(val key: String, val headerName: String = "X-API-Key")
    data class Basic(val username: String, val password: String)
    data class Custom(val type: String, val properties: Map<String, String>)
}
```

Exhaustive sealed interface — all credential types are known at compile time. `Custom` is the escape hatch for proprietary auth schemes.

### CredentialProvider

```kotlin
interface CredentialProvider {
    suspend fun current(): Credential?
}
```

Returns the currently active credential, or `null` if unauthenticated. Implementations should read from `SessionController` or `SecretStore` — never cache stale tokens.

> **Status:** Interface defined. Concrete implementation pending (depends on `SessionController` and `SecretStore` implementations).

### CredentialHeaderMapper

```kotlin
object CredentialHeaderMapper {
    fun toHeaders(credential: Credential): Map<String, String>
}
```

Pure, stateless conversion — no network-core dependency:

| Credential Type | Output |
|---|---|
| `Bearer("abc123")` | `{"Authorization": "Bearer abc123"}` |
| `ApiKey("key", "X-API-Key")` | `{"X-API-Key": "key"}` |
| `Basic("user", "pass")` | `{"Authorization": "Basic dXNlcjpwYXNz"}` |
| `Custom("OAuth2", props)` | `props` as-is |

Uses `Base64.encodeToString()` from the `util/` package for Basic auth encoding.

### SessionController

```kotlin
interface SessionController {
    val state: StateFlow<SessionState>       // Reactive session state
    val events: Flow<SessionEvent>           // Lifecycle events stream
    suspend fun startSession(credentials: SessionCredentials)
    suspend fun refreshSession(): Boolean
    suspend fun endSession()
}
```

`SessionState` is a sealed interface:

```kotlin
sealed interface SessionState {
    data object Idle : SessionState               // No active session
    data class Active(val credential: Credential) // Authenticated
    data object Expired : SessionState            // Session expired, refresh needed
}
```

`SessionEvent` captures lifecycle transitions:

```kotlin
sealed class SessionEvent {
    data object Started
    data object Refreshed
    data object Expired
    data object Ended
    data class RefreshFailed(val error: SecurityError)
}
```

> **Status:** Interface defined with `StateFlow` for reactive UI observation. Implementation pending.

### SecretStore

```kotlin
interface SecretStore {
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun putBytes(key: String, value: ByteArray)
    suspend fun getBytes(key: String): ByteArray?
    suspend fun remove(key: String)
    suspend fun clear()
    suspend fun contains(key: String): Boolean
}
```

All operations are `suspend` — platform storage APIs may involve I/O.

### TrustPolicy

```kotlin
interface TrustPolicy {
    fun evaluateHost(hostname: String): TrustEvaluation
    fun pinnedCertificates(): Map<String, Set<CertificatePin>>
}
```

```kotlin
sealed interface TrustEvaluation {
    data object Trusted
    data class Denied(val reason: String)
}

data class CertificatePin(val algorithm: String, val hash: String)
```

`DefaultTrustPolicy` trusts all hosts (development default). Production apps override with pin sets per hostname.

### LogSanitizer

```kotlin
interface LogSanitizer {
    fun sanitize(key: String, value: String): String
}
```

`DefaultLogSanitizer` uses `SecurityConfig` to identify sensitive keys and replaces their values with `"██"`.

Extension functions for bulk sanitization:

```kotlin
fun LogSanitizer.sanitizeHeaders(headers: Map<String, String>): Map<String, String>
fun LogSanitizer.sanitizeMultiValueHeaders(headers: Map<String, List<String>>): Map<String, List<String>>
```

### SecurityError

```kotlin
sealed class SecurityError {
    abstract val message: String           // Safe for end users
    abstract val diagnostic: Diagnostic?   // Internal debugging only

    class TokenExpired
    class TokenRefreshFailed
    class InvalidCredentials
    class SecureStorageFailure
    class CertificatePinningFailure(val host: String)
    class Unknown
}
```

---

## Internal Structure

```
security-core/src/
├── commonMain/kotlin/com/dancr/platform/security/
│   ├── config/
│   │   └── SecurityConfig.kt              # Sensitive headers/keys, redaction placeholder
│   ├── credential/
│   │   ├── Credential.kt                  # Sealed interface: Bearer, ApiKey, Basic, Custom
│   │   ├── CredentialProvider.kt          # Interface — supplies active credential
│   │   └── CredentialHeaderMapper.kt      # Credential → Map<String, String>
│   ├── error/
│   │   ├── SecurityError.kt               # Semantic error taxonomy (sealed class)
│   │   └── Diagnostic.kt                  # Internal error details
│   ├── sanitizer/
│   │   ├── LogSanitizer.kt                # Interface + extension functions
│   │   └── DefaultLogSanitizer.kt         # Config-driven key redaction
│   ├── session/
│   │   ├── SessionController.kt           # Session lifecycle contract (StateFlow-based)
│   │   ├── SessionState.kt                # Idle | Active(credential) | Expired
│   │   ├── SessionCredentials.kt          # Credential + refreshToken + expiresAtMs
│   │   └── SessionEvent.kt                # Started, Refreshed, Expired, Ended, RefreshFailed
│   ├── store/
│   │   └── SecretStore.kt                 # Secure key-value storage interface
│   ├── trust/
│   │   ├── TrustPolicy.kt                # Host evaluation + cert pinning interface
│   │   ├── TrustEvaluation.kt            # Trusted | Denied(reason)
│   │   ├── CertificatePin.kt             # Algorithm + hash pair
│   │   └── DefaultTrustPolicy.kt         # Always-trust default
│   └── util/
│       └── Base64.kt                      # Cross-platform Base64 encoding
│
├── androidMain/kotlin/com/dancr/platform/security/store/
│   ├── AndroidSecretStore.kt              # SecretStore impl (skeleton)
│   └── AndroidStoreConfig.kt             # Preferences name, master key alias, key prefix
│
└── iosMain/kotlin/com/dancr/platform/security/store/
    ├── IosSecretStore.kt                  # SecretStore impl (skeleton)
    └── KeychainConfig.kt                  # Service name, access group, accessibility level
```

---

## Architecture

### Separation by Concern

```mermaid
graph TD
    subgraph "Credential Management"
        CR[Credential]
        CP[CredentialProvider]
        CHM[CredentialHeaderMapper]
    end

    subgraph "Session Lifecycle"
        SC[SessionController]
        SS[SessionState]
        SE[SessionEvent]
        SCR[SessionCredentials]
    end

    subgraph "Secure Storage"
        STO[SecretStore]
        AS[AndroidSecretStore]
        IS[IosSecretStore]
    end

    subgraph "Trust & TLS"
        TP[TrustPolicy]
        TE[TrustEvaluation]
        PIN[CertificatePin]
    end

    subgraph "Log Protection"
        LS[LogSanitizer]
        DLS[DefaultLogSanitizer]
        CFG[SecurityConfig]
    end

    SC --> SS
    SC --> SE
    SC --> SCR
    SCR --> CR
    SS --> CR
    CP --> CR
    CHM --> CR
    DLS --> CFG
    AS -->|implements| STO
    IS -->|implements| STO

    style CR fill:#e1f5fe,stroke:#0277bd
    style STO fill:#e8f5e9,stroke:#2e7d32
    style SC fill:#fff3e0,stroke:#ef6c00
    style TP fill:#fce4ec,stroke:#c62828
    style LS fill:#f3e5f5,stroke:#7b1fa2
```

### Platform Source Set Distribution

```mermaid
graph LR
    subgraph commonMain["commonMain (all contracts)"]
        A[SecretStore interface]
        B[Credential / CredentialProvider]
        C[SessionController]
        D[TrustPolicy / CertificatePin]
        E[LogSanitizer / SecurityConfig]
        F[SecurityError / Diagnostic]
        G[Base64 utility]
    end

    subgraph androidMain
        H["AndroidSecretStore<br/>(EncryptedSharedPreferences)"]
        I[AndroidStoreConfig]
    end

    subgraph iosMain
        J["IosSecretStore<br/>(Keychain Services)"]
        K["KeychainConfig<br/>+ KeychainAccessibility"]
    end

    A --> H
    A --> J

    style commonMain fill:#e3f2fd,stroke:#1565c0
    style androidMain fill:#e8f5e9,stroke:#2e7d32
    style iosMain fill:#fff3e0,stroke:#ef6c00
```

---

## Usage Examples

### Using CredentialHeaderMapper (no network dependency)

```kotlin
val credential = Credential.Bearer("eyJhbGciOiJIUzI1NiIs...")
val headers = CredentialHeaderMapper.toHeaders(credential)
// {"Authorization": "Bearer eyJhbGciOiJIUzI1NiIs..."}
```

### Building an auth interceptor (in consuming module)

```kotlin
// This code lives in the domain module, NOT in security-core
val authInterceptor = RequestInterceptor { request, _ ->
    val credential = credentialProvider.current()
        ?: return@RequestInterceptor request
    val headers = CredentialHeaderMapper.toHeaders(credential)
    request.copy(headers = request.headers + headers)
}
```

### Observing session state (in UI layer)

```kotlin
// In a ViewModel
sessionController.state.collect { state ->
    when (state) {
        is SessionState.Idle -> showLoginScreen()
        is SessionState.Active -> showHomeScreen()
        is SessionState.Expired -> showSessionExpiredDialog()
    }
}
```

### Sanitizing logs

```kotlin
val sanitizer = DefaultLogSanitizer()
val rawHeaders = mapOf("Authorization" to "Bearer secret123", "Accept" to "application/json")
val safe = sanitizer.sanitizeHeaders(rawHeaders)
// {"Authorization": "██", "Accept": "application/json"}
```

### Configuring sensitive keys

```kotlin
val config = SecurityConfig(
    sensitiveHeaders = SecurityConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-secret"),
    sensitiveKeys = SecurityConfig.DEFAULT_SENSITIVE_KEYS + setOf("pin_code"),
    redactedPlaceholder = "[REDACTED]"
)
val sanitizer = DefaultLogSanitizer(config)
```

### Trust evaluation

```kotlin
class ProductionTrustPolicy : TrustPolicy {
    override fun evaluateHost(hostname: String): TrustEvaluation {
        val allowed = setOf("api.mycompany.com", "auth.mycompany.com")
        return if (hostname in allowed) TrustEvaluation.Trusted
               else TrustEvaluation.Denied("Host $hostname is not in the allowed list")
    }

    override fun pinnedCertificates() = mapOf(
        "api.mycompany.com" to setOf(
            CertificatePin("sha256", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )
    )
}
```

---

## Design Decisions

| Decision | Rationale |
|---|---|
| **Zero dependency on `:network-core`** | Security concerns (credentials, storage, trust) are fundamentally independent of HTTP transport. A module that only needs secure storage should not pull in the entire networking stack. |
| **`CredentialHeaderMapper` returns `Map<String, String>`** | The mapper converts credentials to plain header pairs without importing `HttpRequest`, `RequestInterceptor`, or any network type. This keeps the boundary clean. |
| **`SessionController.state` is `StateFlow`** | Enables reactive UI observation. A `val state: SessionState` property would require polling. `StateFlow` gives subscribers immediate access to the current value and reactive updates. |
| **`Credential` is a sealed interface** | Compile-time exhaustive matching via `when`. All credential types are known, which prevents runtime surprises. `Custom` is the escape hatch for proprietary schemes. |
| **Platform implementations are skeletons with TODOs** | The interface and architecture are proven. The platform implementations require careful integration with Android Keystore and iOS Keychain APIs, which are high-risk code that should be implemented with proper testing infrastructure. |
| **`SecurityError` mirrors `NetworkError` structure** | Both use sealed classes with `message` (user-safe) + `diagnostic` (internal). This parallel structure simplifies error handling in consumers that bridge both modules. |
| **`Base64` is a manual implementation** | Kotlin common stdlib does not provide Base64 encoding. The manual implementation avoids a dependency on `kotlinx-io` or platform-specific APIs for a trivial utility. |
| **`DefaultLogSanitizer` uses key matching, not pattern matching** | Simple, predictable, and fast. If a key is in the sensitive set, its value is fully redacted. No regex, no partial redaction, no false negatives. |

---

## Platform Implementation Details

### Android: `AndroidSecretStore`

| Aspect | Detail |
|---|---|
| **Backend** | `EncryptedSharedPreferences` + Android Keystore |
| **Encryption** | AES-256-GCM for values, AES-256-SIV for keys |
| **Key management** | `MasterKey` with configurable StrongBox backing |
| **Threading** | All operations dispatched to `Dispatchers.IO` |
| **Configuration** | `AndroidStoreConfig` — preferences name, master key alias, key prefix, StrongBox flag |
| **Status** | Skeleton with step-by-step TODOs. Requires `androidx.security:security-crypto:1.1.0-alpha06`. |

### iOS: `IosSecretStore`

| Aspect | Detail |
|---|---|
| **Backend** | Keychain Services (`kSecClassGenericPassword`) |
| **APIs** | `SecItemAdd`, `SecItemCopyMatching`, `SecItemUpdate`, `SecItemDelete` |
| **Configuration** | `KeychainConfig` — service name, access group, accessibility level |
| **Accessibility levels** | `WHEN_UNLOCKED`, `AFTER_FIRST_UNLOCK`, `WHEN_PASSCODE_SET_THIS_DEVICE_ONLY`, device-only variants |
| **Threading** | All operations dispatched to `Dispatchers.IO` |
| **Status** | Skeleton with step-by-step TODOs. Uses `platform.Security` framework via cinterop (built-in). |

---

## Extensibility

| Extension Point | How |
|---|---|
| **New credential type** | Add to `Credential` sealed interface + update `CredentialHeaderMapper.toHeaders()` |
| **Custom credential provider** | Implement `CredentialProvider` backed by your auth system |
| **Custom secret store** | Implement `SecretStore` for a different backend (e.g., SQLCipher, in-memory for testing) |
| **Custom trust policy** | Implement `TrustPolicy` with your pin sets and host rules |
| **Custom log sanitization** | Implement `LogSanitizer` with pattern-based or context-aware redaction |
| **New session events** | Add to `SessionEvent` sealed class (requires modifying the file) |
| **New security errors** | Add to `SecurityError` sealed class |

---

## Current Limitations

| Limitation | Context |
|---|---|
| **Platform `SecretStore` implementations are skeletons** | `AndroidSecretStore` and `IosSecretStore` have TODO bodies with detailed implementation guidance, but do not compile to working code yet. |
| **No `SessionController` implementation** | The interface and state model are defined, but no `DefaultSessionController` exists. This requires working `SecretStore` implementations first. |
| **`Diagnostic` is duplicated with `:network-core`** | Both modules define identical `Diagnostic` data classes. A future `:platform-common` module should unify them. |
| **No biometric authentication** | `SecretStore` does not support biometric-gated access (e.g., `setUserAuthenticationRequired` on Android, `kSecAccessControlBiometryAny` on iOS). |
| **`CredentialProvider` lacks refresh/invalidate hooks** | Current interface only has `current()`. Token refresh and invalidation require future `refresh()` and `invalidate()` methods. |

---

## TODOs and Future Work

| Item | Location | Description |
|---|---|---|
| Complete `AndroidSecretStore` | `androidMain/store/` | Wire EncryptedSharedPreferences with MasterKey + error mapping |
| Complete `IosSecretStore` | `iosMain/store/` | Wire Keychain Services with proper OSStatus error handling |
| `DefaultSessionController` | `session/` | StateFlow-based implementation with token storage and refresh logic |
| `CredentialProvider` impl | `credential/` | Backed by `SessionController` + `SecretStore` |
| `refresh()` on `CredentialProvider` | `credential/` | Proactive token refresh before expiry |
| `invalidate()` on `CredentialProvider` | `credential/` | Clear cached credential on 401 |
| `invalidate()` on `SessionController` | `session/` | Force-logout from any layer |
| `RefreshOutcome` sealed result | `session/` | Replace `Boolean` return from `refreshSession()` with semantic result |
| `isAuthenticated` convenience | `SessionController` | Derived from `state` for simple checks |
| `keys()` on `SecretStore` | `store/` | Enumerate stored keys for migration/diagnostics |
| `putStringIfAbsent()` on `SecretStore` | `store/` | Atomic write-if-missing for race-free initialization |
| Biometric integration | `store/` | Platform-specific biometric gating for secret access |
| Unify `Diagnostic` | cross-module | Extract to `:platform-common` shared module |

---

## Dependencies

```toml
# Only dependency — no networking, no serialization
[dependencies]
kotlinx-coroutines-core = "1.10.1"
```

This module compiles to **all targets**: Android, iosX64, iosArm64, iosSimulatorArm64.
