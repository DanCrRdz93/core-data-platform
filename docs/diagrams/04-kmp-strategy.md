# KMP Source Set Strategy

Distribution of code across Kotlin Multiplatform source sets. The project maximizes `commonMain` and pushes platform-specific code to the absolute edges.

## Source Set Distribution

![KMP Source Set Distribution](images/04-kmp-strategy.svg)

<details>
<summary>Mermaid source</summary>

```mermaid
graph TB
    subgraph commonMain["commonMain — 43 files (95%+)"]
        direction TB
        subgraph nc_common["network-core (20 files)"]
            NC1["HttpEngine, HttpRequest, RawResponse, HttpMethod"]
            NC2["SafeRequestExecutor, DefaultSafeRequestExecutor"]
            NC3["RequestInterceptor, ResponseInterceptor"]
            NC4["ErrorClassifier, DefaultErrorClassifier"]
            NC5["ResponseValidator, DefaultResponseValidator"]
            NC6["NetworkEventObserver"]
            NC7["NetworkResult, NetworkError, Diagnostic"]
            NC8["NetworkConfig, RetryPolicy, RequestContext"]
            NC9["RemoteDataSource, ResponseMetadata"]
        end

        subgraph nk_common["network-ktor (2 files)"]
            NK1["KtorHttpEngine"]
            NK2["KtorErrorClassifier"]
        end

        subgraph sc_common["security-core (15 files)"]
            SC1["Credential, CredentialProvider, CredentialHeaderMapper"]
            SC2["SessionController, SessionState, SessionEvent, SessionCredentials"]
            SC3["SecretStore"]
            SC4["TrustPolicy, TrustEvaluation, CertificatePin, DefaultTrustPolicy"]
            SC5["LogSanitizer, DefaultLogSanitizer, SecurityConfig"]
            SC6["SecurityError, Diagnostic, Base64"]
        end

        subgraph sa_common["sample-api (6 files)"]
            SA1["UserDto, User, UserMapper"]
            SA2["UserRemoteDataSource, UserRepository, SampleApiFactory"]
        end
    end

    subgraph androidMain["androidMain — 2 files"]
        AND1["AndroidSecretStore<br/><i>EncryptedSharedPreferences<br/>+ Android Keystore</i>"]
        AND2["AndroidStoreConfig"]
    end

    subgraph iosMain["iosMain — 2 files"]
        IOS1["IosSecretStore<br/><i>Keychain Services</i>"]
        IOS2["KeychainConfig<br/>+ KeychainAccessibility"]
    end

    SC3 -->|"implements"| AND1
    SC3 -->|"implements"| IOS1

    style commonMain fill:#e3f2fd,stroke:#1565c0
    style androidMain fill:#e8f5e9,stroke:#2e7d32
    style iosMain fill:#fff3e0,stroke:#ef6c00
    style nc_common fill:#bbdefb,stroke:#1565c0
    style nk_common fill:#c8e6c9,stroke:#2e7d32
    style sc_common fill:#f8bbd0,stroke:#c62828
    style sa_common fill:#ffe0b2,stroke:#ef6c00
```

</details>

## Decision Rules

![Decision Rules](images/04b-decision-rules.svg)

<details>
<summary>Mermaid source</summary>

```mermaid
flowchart TD
    Q1{"Does it call a<br/>platform-specific API?"}
    Q2{"Is it a configuration<br/>data class?"}
    Q3{"Does it map to<br/>platform constants?"}

    COMMON["→ commonMain"]
    PLATFORM["→ androidMain / iosMain"]

    Q1 -->|No| COMMON
    Q1 -->|Yes| Q2
    Q2 -->|"Yes (plain data class)"| COMMON
    Q2 -->|"No (calls platform API)"| PLATFORM
    Q3 -->|"Yes (enum → platform const)"| PLATFORM

    style COMMON fill:#e3f2fd,stroke:#1565c0
    style PLATFORM fill:#fff3e0,stroke:#ef6c00
```

</details>

| Rule | Example |
|---|---|
| Interfaces and contracts → `commonMain` | `SecretStore`, `HttpEngine`, `TrustPolicy` |
| Sealed classes and data models → `commonMain` | `NetworkError`, `Credential`, `SessionState` |
| Default implementations (pure logic) → `commonMain` | `DefaultSafeRequestExecutor`, `DefaultLogSanitizer` |
| Configuration data classes → `commonMain` | `AndroidStoreConfig`, `KeychainConfig`, `NetworkConfig` |
| Platform I/O → platform source set | `AndroidSecretStore`, `IosSecretStore` |
| Platform enums → platform source set | `KeychainAccessibility` (maps to `kSecAttrAccessible*`) |
| Ktor engine selection → Gradle dependencies | `ktor-client-okhttp` (androidMain.dependencies), `ktor-client-darwin` (iosMain.dependencies) |
