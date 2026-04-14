# Relaciones Principales de Contratos

Cómo las interfaces principales, sealed classes e implementaciones se relacionan entre sí a través del SDK.

## Contratos del Pipeline de Ejecución

![Execution Pipeline Contracts](images/05a-execution-pipeline.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
classDiagram
    direction TB

    class SafeRequestExecutor {
        <<interface>>
        +execute(request, context?, deserialize) NetworkResult~T~
    }

    class DefaultSafeRequestExecutor {
        -engine: HttpEngine
        -config: NetworkConfig
        -validator: ResponseValidator
        -classifier: ErrorClassifier
        -interceptors: List~RequestInterceptor~
        -responseInterceptors: List~ResponseInterceptor~
        -observers: List~NetworkEventObserver~
        +execute(request, context?, deserialize) NetworkResult~T~
    }

    class HttpEngine {
        <<interface>>
        +execute(request) RawResponse
        +close()
    }

    class KtorHttpEngine {
        -client: HttpClient
        +execute(request) RawResponse
        +close()
        +create(config)$ KtorHttpEngine
    }

    class RequestInterceptor {
        <<fun interface>>
        +intercept(request, context?) HttpRequest
    }

    class ResponseInterceptor {
        <<fun interface>>
        +intercept(response, request, context?) RawResponse
    }

    class ErrorClassifier {
        <<interface>>
        +classify(response?, cause?) NetworkError
    }

    class DefaultErrorClassifier {
        <<open>>
        +classify(response?, cause?) NetworkError
        #classifyThrowable(cause) NetworkError
        #classifyResponse(response) NetworkError
    }

    class KtorErrorClassifier {
        #classifyThrowable(cause) NetworkError
    }

    class ResponseValidator {
        <<interface>>
        +validate(response) ValidationOutcome
    }

    class DefaultResponseValidator {
        +validate(response) ValidationOutcome
    }

    class NetworkEventObserver {
        <<interface>>
        +onRequestStarted(request, context?)
        +onResponseReceived(request, response, durationMs, context?)
        +onRetryScheduled(request, attempt, max, error, delayMs)
        +onRequestFailed(request, error, durationMs, context?)
    }

    SafeRequestExecutor <|.. DefaultSafeRequestExecutor
    HttpEngine <|.. KtorHttpEngine
    ErrorClassifier <|.. DefaultErrorClassifier
    DefaultErrorClassifier <|-- KtorErrorClassifier
    ResponseValidator <|.. DefaultResponseValidator

    DefaultSafeRequestExecutor --> HttpEngine : uses
    DefaultSafeRequestExecutor --> ErrorClassifier : uses
    DefaultSafeRequestExecutor --> ResponseValidator : uses
    DefaultSafeRequestExecutor --> RequestInterceptor : chains
    DefaultSafeRequestExecutor --> ResponseInterceptor : chains
    DefaultSafeRequestExecutor --> NetworkEventObserver : notifies
```

</details>

## Modelo de Resultado y Error

![Result and Error Model](images/05b-result-error-model.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
classDiagram
    direction TB

    class NetworkResult~T~ {
        <<sealed>>
        +isSuccess: Boolean
        +isFailure: Boolean
        +map(transform) NetworkResult~R~
        +flatMap(transform) NetworkResult~R~
        +fold(onSuccess, onFailure) R
        +onSuccess(action) NetworkResult~T~
        +onFailure(action) NetworkResult~T~
        +getOrNull() T?
        +errorOrNull() NetworkError?
    }

    class Success~T~ {
        +data: T
        +metadata: ResponseMetadata
    }

    class Failure {
        +error: NetworkError
    }

    class ResponseMetadata {
        +statusCode: Int
        +headers: Map
        +durationMs: Long
        +requestId: String?
        +attemptCount: Int
    }

    class NetworkError {
        <<sealed>>
        +message: String
        +diagnostic: Diagnostic?
        +isRetryable: Boolean
    }

    class Connectivity { +isRetryable = true }
    class Timeout { +isRetryable = true }
    class Cancelled { +isRetryable = false }
    class Authentication { +isRetryable = false }
    class Authorization { +isRetryable = false }
    class ClientError { +statusCode: Int }
    class ServerError { +statusCode: Int&#59; isRetryable = true }
    class Serialization { +isRetryable = false }
    class ResponseValidation { +reason: String }
    class Unknown { +isRetryable = false }

    class Diagnostic {
        +description: String
        +cause: Throwable?
        +metadata: Map
    }

    NetworkResult <|-- Success
    NetworkResult <|-- Failure
    Success --> ResponseMetadata
    Failure --> NetworkError
    NetworkError <|-- Connectivity
    NetworkError <|-- Timeout
    NetworkError <|-- Cancelled
    NetworkError <|-- Authentication
    NetworkError <|-- Authorization
    NetworkError <|-- ClientError
    NetworkError <|-- ServerError
    NetworkError <|-- Serialization
    NetworkError <|-- ResponseValidation
    NetworkError <|-- Unknown
    NetworkError --> Diagnostic
```

</details>

## Contratos de Seguridad

![Security Contracts](images/05c-security-contracts.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
classDiagram
    direction TB

    class Credential {
        <<sealed interface>>
    }
    class Bearer { +token: String }
    class ApiKey { +key: String&#59; +headerName: String }
    class Basic { +username: String&#59; +password: String }
    class Custom { +type: String&#59; +properties: Map }

    class CredentialProvider {
        <<interface>>
        +current() Credential?
    }

    class CredentialHeaderMapper {
        <<object>>
        +toHeaders(credential) Map~String,String~
    }

    class SessionController {
        <<interface>>
        +state: StateFlow~SessionState~
        +events: Flow~SessionEvent~
        +startSession(credentials)
        +refreshSession() Boolean
        +endSession()
    }

    class SessionState {
        <<sealed interface>>
    }
    class Idle
    class Active { +credential: Credential }
    class Expired

    class SessionCredentials {
        +credential: Credential
        +refreshToken: String?
        +expiresAtMs: Long?
    }

    class SecretStore {
        <<interface>>
        +putString(key, value)
        +getString(key) String?
        +putBytes(key, value)
        +getBytes(key) ByteArray?
        +remove(key)
        +clear()
        +contains(key) Boolean
    }

    class TrustPolicy {
        <<interface>>
        +evaluateHost(hostname) TrustEvaluation
        +pinnedCertificates() Map
    }

    class LogSanitizer {
        <<interface>>
        +sanitize(key, value) String
    }

    Credential <|.. Bearer
    Credential <|.. ApiKey
    Credential <|.. Basic
    Credential <|.. Custom
    CredentialProvider --> Credential : provides
    CredentialHeaderMapper --> Credential : converts
    SessionController --> SessionState : exposes
    SessionState <|.. Idle
    SessionState <|.. Active
    SessionState <|.. Expired
    Active --> Credential : contains
    SessionCredentials --> Credential : wraps
    SessionController --> SessionCredentials : receives
```

</details>

## Integración Cross-Module

![Cross-Module Integration](images/06-cross-module-integration.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
graph LR
    subgraph security-core["security-core"]
        CP["CredentialProvider"]
        CHM["CredentialHeaderMapper"]
        CR["Credential"]
        CP -->|"current()"| CR
        CHM -->|"toHeaders()"| CR
    end

    subgraph network-core["network-core"]
        RI["RequestInterceptor"]
        HR["HttpRequest"]
        RI -->|"modifies"| HR
    end

    subgraph consumer["Módulo de Dominio"]
        AI["Auth Interceptor"]
        AI -->|"calls"| CP
        AI -->|"calls"| CHM
        AI -->|"implements"| RI
    end

    CHM -->|"Map&lt;String,String&gt;"| AI

    style security-core fill:#fce4ec,stroke:#c62828
    style network-core fill:#e1f5fe,stroke:#0277bd
    style consumer fill:#fff3e0,stroke:#ef6c00
```

</details>

El módulo de dominio es el **único lugar** donde los tipos de `security-core` y `network-core` se encuentran. El puente es un `RequestInterceptor` que llama a `CredentialProvider.current()`, pasa el resultado por `CredentialHeaderMapper.toHeaders()`, y combina los headers en el `HttpRequest`.
