# Estrategia de Source Sets KMP

Distribución de código entre source sets de Kotlin Multiplatform. El proyecto maximiza `commonMain` y empuja el código específico de plataforma a los bordes absolutos.

## Distribución de Source Sets

![KMP Source Set Distribution](images/04-kmp-strategy.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
graph TB
    subgraph commonMain["commonMain — 43 archivos (95%+)"]
        direction LR
        subgraph nc_common["network-core (20 files)"]
            NC1["HttpEngine, HttpRequest,<br/>RawResponse, HttpMethod"]
            NC2["SafeRequestExecutor,<br/>DefaultSafeRequestExecutor"]
            NC3["RequestInterceptor,<br/>ResponseInterceptor"]
            NC4["ErrorClassifier,<br/>DefaultErrorClassifier"]
            NC5["ResponseValidator,<br/>DefaultResponseValidator"]
            NC6["NetworkEventObserver,<br/>LoggingObserver,<br/>MetricsObserver,<br/>TracingObserver"]
            NC7["NetworkResult,<br/>NetworkError, Diagnostic"]
            NC8["NetworkConfig,<br/>RetryPolicy, RequestContext"]
            NC9["RemoteDataSource,<br/>ResponseMetadata"]
        end

        subgraph nk_common["network-ktor (4 files)"]
            NK1["KtorHttpEngine"]
            NK2["KtorErrorClassifier"]
            NK3["PlatformHttpClient"]
        end

        subgraph sc_common["security-core (15 files)"]
            SC1["Credential, CredentialProvider,<br/>CredentialHeaderMapper"]
            SC2["SessionController, SessionState,<br/>SessionEvent, SessionCredentials"]
            SC3["SecretStore"]
            SC4["TrustPolicy, TrustEvaluation,<br/>CertificatePin, DefaultTrustPolicy"]
            SC5["LogSanitizer,<br/>DefaultLogSanitizer,<br/>SecurityConfig"]
            SC6["SecurityError, Diagnostic,<br/>Base64"]
        end

        subgraph sa_common["sample-api (6 files)"]
            SA1["UserDto, User, UserMapper"]
            SA2["UserRemoteDataSource,<br/>UserRepository,<br/>SampleApiFactory"]
        end
    end

    subgraph platformMain["Platform Source Sets"]
        direction LR
        subgraph androidMain["androidMain — 3 archivos"]
            AND1["AndroidSecretStore<br/><i>EncryptedSharedPreferences<br/>+ Android Keystore</i>"]
            AND2["AndroidStoreConfig"]
            AND3["PlatformHttpClient.android<br/><i>OkHttp + CertificatePinner</i>"]
        end

        subgraph iosMain["iosMain — 3 archivos"]
            IOS1["IosSecretStore<br/><i>Keychain Services</i>"]
            IOS2["KeychainConfig<br/>+ KeychainAccessibility"]
            IOS3["PlatformHttpClient.ios<br/><i>Darwin + SecTrust</i>"]
        end
    end

    SC3 -->|"implements"| AND1
    SC3 -->|"implements"| IOS1
    NK3 -->|"actual"| AND3
    NK3 -->|"actual"| IOS3

    style commonMain fill:#e3f2fd,stroke:#1565c0
    style platformMain fill:#fafafa,stroke:#9e9e9e
    style androidMain fill:#e8f5e9,stroke:#2e7d32
    style iosMain fill:#fff3e0,stroke:#ef6c00
    style nc_common fill:#bbdefb,stroke:#1565c0
    style nk_common fill:#c8e6c9,stroke:#2e7d32
    style sc_common fill:#f8bbd0,stroke:#c62828
    style sa_common fill:#ffe0b2,stroke:#ef6c00
```

</details>

## Reglas de Decisión

![Decision Rules](images/04b-decision-rules.svg)

<details>
<summary>Código fuente Mermaid</summary>

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
    Q2 -->|"No (calls platform API)"| Q3
    Q3 -->|"No (pure logic)"| PLATFORM
    Q3 -->|"Yes (enum → platform const)"| PLATFORM

    style COMMON fill:#e3f2fd,stroke:#1565c0
    style PLATFORM fill:#fff3e0,stroke:#ef6c00
    style Q1 fill:#fff9c4,stroke:#f9a825
    style Q2 fill:#fff9c4,stroke:#f9a825
    style Q3 fill:#fff9c4,stroke:#f9a825
```

</details>

| Regla | Ejemplo |
|---|---|
| Interfaces y contratos → `commonMain` | `SecretStore`, `HttpEngine`, `TrustPolicy` |
| Sealed classes y modelos de datos → `commonMain` | `NetworkError`, `Credential`, `SessionState` |
| Implementaciones por defecto (lógica pura) → `commonMain` | `DefaultSafeRequestExecutor`, `DefaultLogSanitizer` |
| Data classes de configuración → `commonMain` | `AndroidStoreConfig`, `KeychainConfig`, `NetworkConfig` |
| I/O de plataforma → source set de plataforma | `AndroidSecretStore`, `IosSecretStore` |
| Enums de plataforma → source set de plataforma | `KeychainAccessibility` (mapea a `kSecAttrAccessible*`) |
| Selección de engine Ktor → dependencias Gradle | `ktor-client-okhttp` (androidMain.dependencies), `ktor-client-darwin` (iosMain.dependencies) |
