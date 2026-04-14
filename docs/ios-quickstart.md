# Guía Rápida para iOS

**Cómo usar el Core Data Platform SDK en un proyecto iOS con Clean Architecture.**

---

## ¿Qué es esto?

Es un SDK interno construido con Kotlin Multiplatform que te da networking y seguridad listos para usar. En vez de configurar URLSession directamente, manejar errores manualmente, o escribir lógica de retry en cada pantalla, este SDK te da:

- **Llamadas HTTP** con retry automático, clasificación de errores, y timeouts configurables.
- **Manejo de credenciales** (Bearer token, API Key, Basic Auth) sin que tú toques headers.
- **Almacenamiento seguro** vía iOS Keychain, encapsulado en `IosSecretStore`.
- **Un resultado tipado** (`NetworkResult<T>`) que es `Success` o `Failure` — nunca excepciones sueltas.

Tú solo interactúas con **repositorios** que devuelven modelos de dominio limpios. Todo lo demás (transporte HTTP, serialización, retry, headers de auth) sucede por debajo.

---

## Arquitectura: dónde encaja el SDK

```
┌─────────────────────────────────────────────────┐
│  Presentation (SwiftUI + ViewModel)             │
│  • Solo conoce protocolos del domain layer      │
│  • Recibe dependencias vía init (DI)            │
├─────────────────────────────────────────────────┤
│  Domain (Use Cases + Protocolos)                │
│  • Define UserRepositoryProtocol                │
│  • Contiene lógica de negocio si aplica         │
│  • No importa el SDK directamente               │
├─────────────────────────────────────────────────┤
│  Data (SDK + Adapters)                          │
│  • SampleApiFactory.create() → UserRepository   │
│  • Adapter implementa UserRepositoryProtocol    │
│  • IosSecretStore para almacenamiento seguro    │
├─────────────────────────────────────────────────┤
│  Core Data Platform SDK (framework KMP)         │
│  • network-core, network-ktor, security-core    │
└─────────────────────────────────────────────────┘
```

**Principio clave:** tu ViewModel nunca sabe que el SDK existe. Solo conoce un protocolo que le provee datos.

---

## Configuración en Xcode

### 1. Agrega el framework KMP

El SDK se distribuye como un XCFramework generado por Gradle. En tu `build.gradle.kts` del módulo compartido:

```kotlin
kotlin {
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "CoreDataPlatform"
            isStatic = true
        }
    }
}
```

En Xcode, agrega el framework generado (en `build/bin/ios.../releaseFramework/`) a tu target:
- **General** → **Frameworks, Libraries, and Embedded Content** → agrega `CoreDataPlatform.framework`.

### 2. Importa en Swift

```swift
import CoreDataPlatform
```

Todas las clases Kotlin del SDK estarán disponibles como clases Objective-C compatibles.

---

## Ejemplo completo: integración del SDK

> **Nota:** Los ejemplos de esta guía usan `User` y `UserRepository` del módulo `:sample-api`, que es un **módulo piloto de referencia**. En tu proyecto real, sustituirás esto por tus propios módulos de dominio (ej. `:payments-api`, `:loyalty-api`) siguiendo el mismo patrón.

### Paso 1 — Capa Domain: define el contrato

Define un protocolo Swift que abstraiga el repository. Las capas superiores (ViewModel, Use Cases) solo conocerán este protocolo.

```swift
// Domain/Protocols/UserRepositoryProtocol.swift

import Foundation

protocol UserRepositoryProtocol {
    func getUsers() async throws -> [User]
    func getUser(id: Int64) async throws -> User
}
```

> **Principio SOLID (D — Dependency Inversion):** El ViewModel depende de una abstracción (`UserRepositoryProtocol`), no del `UserRepository` concreto del SDK.

---

## Paso 2 — Capa Domain: modelo de dominio Swift (opcional)

Si prefieres modelos Swift nativos en vez de las clases KMP exportadas:

```swift
// Domain/Models/UserModel.swift

struct UserModel: Identifiable {
    let id: Int64
    let displayName: String
    let handle: String
    let email: String
    let company: String?
}
```

---

## Paso 3 — Capa Data: adapter que conecta el SDK con tu protocolo

```swift
// Data/Repositories/UserRepositoryAdapter.swift

import CoreDataPlatform

final class UserRepositoryAdapter: UserRepositoryProtocol {

    private let repository: UserRepository

    init(repository: UserRepository) {
        self.repository = repository
    }

    func getUsers() async throws -> [User] {
        let result = try await repository.getUsers()
        return try mapResult(result)
    }

    func getUser(id: Int64) async throws -> User {
        let result = try await repository.getUser(id: id)
        return try mapResult(result)
    }

    // MARK: - Mapping

    private func mapResult<T>(_ result: NetworkResult<T>) throws -> T {
        switch result {
        case let success as NetworkResultSuccess<T>:
            return success.data
        case let failure as NetworkResultFailure:
            throw NetworkErrorMapper.toSwiftError(failure.error)
        default:
            throw NSError(domain: "CoreDataPlatform", code: -1)
        }
    }
}
```

> **Principio SOLID (S — Single Responsibility):** El adapter solo traduce entre el SDK y tu protocolo. No contiene lógica de negocio ni de UI.

---

## Paso 4 — Error mapping: de `NetworkError` a Swift

```swift
// Data/Mappers/NetworkErrorMapper.swift

import CoreDataPlatform

enum AppNetworkError: LocalizedError {
    case connectivity
    case timeout
    case authentication
    case authorization
    case serverError(statusCode: Int32)
    case serialization
    case unknown(String)

    var errorDescription: String? {
        switch self {
        case .connectivity:            return "No se pudo conectar al servidor"
        case .timeout:                 return "La solicitud tardó demasiado"
        case .authentication:          return "Autenticación requerida"
        case .authorization:           return "Acceso denegado"
        case .serverError:             return "Error del servidor"
        case .serialization:           return "Error procesando la respuesta"
        case .unknown(let msg):        return msg
        }
    }
}

enum NetworkErrorMapper {
    static func toSwiftError(_ error: NetworkError) -> AppNetworkError {
        switch error {
        case is NetworkError.Connectivity:   return .connectivity
        case is NetworkError.Timeout:        return .timeout
        case is NetworkError.Authentication: return .authentication
        case is NetworkError.Authorization:  return .authorization
        case let se as NetworkError.ServerError:
            return .serverError(statusCode: se.statusCode)
        case is NetworkError.Serialization:  return .serialization
        default:
            return .unknown(error.message)
        }
    }
}
```

---

## Paso 5 — Inyección de dependencias

```swift
// DI/DependencyContainer.swift

import CoreDataPlatform

final class DependencyContainer {

    static let shared = DependencyContainer()

    // MARK: - Data layer

    private lazy var sdkRepository: UserRepository = {
        SampleApiFactory.shared.create(config: nil, credentialProvider: nil)
    }()

    // MARK: - Domain layer (protocolos)

    lazy var userRepository: UserRepositoryProtocol = {
        UserRepositoryAdapter(repository: sdkRepository)
    }()
}
```

> **Principio SOLID (D — Dependency Inversion):** El container crea las implementaciones concretas, pero expone solo protocolos. Toda la app depende de abstracciones.

### Paso 6 — Consumir desde la capa domain

Desde un Use Case, ViewModel, o cualquier componente de tu app, consume el protocolo inyectado:

```swift
// El consumidor solo conoce el protocolo — no sabe que el SDK existe
do {
    let users = try await repository.getUsers()
    // modelos de dominio limpios
} catch let error as AppNetworkError {
    // error.localizedDescription es seguro para el usuario
}
```

La capa de presentación (ViewModel, SwiftUI, etc.) queda a criterio de tu arquitectura. Lo importante es que **nunca importe `CoreDataPlatform` directamente** — solo el protocolo definido en el Paso 1.

---

## ¿Necesitas autenticación?

Usa `IosSecretStore` + `DefaultSessionController` + `DefaultCredentialProvider` para un flujo completo de sesión:

```swift
import CoreDataPlatform

// 1. Configura el almacenamiento seguro (Keychain)
let secretStore = IosSecretStore(config: KeychainConfig(
    serviceName: "com.tuapp.secrets",
    accessibility: .afterFirstUnlock
))

// 2. Crea el session controller con tu lógica de refresh
let sessionController = DefaultSessionController(
    store: secretStore,
    refreshTokenProvider: { refreshToken in
        // Tu lógica para llamar al endpoint de refresh
        return try await authService.refresh(token: refreshToken)
    }
)

// 3. Crea el credential provider
let credentialProvider = DefaultCredentialProvider(
    sessionController: sessionController
)

// 4. Pásalo al factory
let repository = SampleApiFactory.shared.create(
    config: nil,
    credentialProvider: credentialProvider
)
```

El SDK automáticamente:
- Llama a `credentialProvider.current()` antes de cada request
- Agrega el header `Authorization: Bearer <token>` si hay sesión activa
- Si no hay sesión, la request va sin auth

### Refresh, invalidación y estado de sesión

```swift
// Consultar si hay sesión activa (derivado del estado, nunca estado duplicado)
if sessionController.isAuthenticated { /* ... */ }

// Refresh proactivo (ej. antes de que expire el token)
let outcome = try await sessionController.refreshSession()
switch outcome {
case let refreshed as RefreshOutcome.Refreshed:
    // nueva credencial activa: refreshed.credential
    break
case let notNeeded as RefreshOutcome.NotNeeded:
    // no había refresh token o provider — estado sin cambios
    break
case let failed as RefreshOutcome.Failed:
    // falló — sesión expirada: failed.error
    break
default: break
}

// Force-logout desde cualquier capa (ej. al recibir un 401)
try await sessionController.invalidate()  // limpia credenciales, emite Invalidated
// vs. endSession() que es logout intencional del usuario y emite Ended
```

> `credentialProvider.invalidate()` delega a `sessionController.invalidate()`. Útil cuando el auth interceptor detecta un 401 sin acceso directo al controller.

---

## ¿Necesitas configuración personalizada?

```swift
let config = NetworkConfig(
    baseUrl: "https://api.tuempresa.com/v1",
    defaultHeaders: ["Accept": "application/json", "X-App-Version": "1.0.0"],
    connectTimeout: KotlinDuration.companion.seconds(10),
    readTimeout: KotlinDuration.companion.seconds(20),
    retryPolicy: RetryPolicy.ExponentialBackoff(
        maxRetries: 3,
        initialDelay: KotlinDuration.companion.seconds(1),
        maxDelay: KotlinDuration.companion.seconds(15)
    )
)

let repository = SampleApiFactory.shared.create(
    config: config,
    credentialProvider: nil
)
```

---

## Logging y observabilidad

El SDK incluye `LoggingObserver`, un observer que registra el ciclo de vida de cada request HTTP. **Por defecto es no-op** — no imprime nada a menos que tú le pases un `NetworkLogger`.

### Configuración básica

```swift
import CoreDataPlatform

// 1. Define tu backend de logging (os_log, print, CocoaLumberjack, etc.)
let logger = NetworkLogger { level, tag, message in
    switch level {
    case .debug: print("[\(tag)] DEBUG: \(message)")
    case .info:  print("[\(tag)] INFO: \(message)")
    case .warn:  print("[\(tag)] WARN: \(message)")
    case .error: print("[\(tag)] ERROR: \(message)")
    default: break
    }
}

// 2. Crea el observer
let loggingObserver = LoggingObserver(logger: logger)

// 3. Pásalo al factory
let repository = SampleApiFactory.shared.create(
    config: nil,
    credentialProvider: nil,
    observers: [loggingObserver]
)
```

Output de ejemplo:
```
[CoreDataPlatform] DEBUG: --> GET https://api.ejemplo.com/users [Accept: application/json]
[CoreDataPlatform] INFO: <-- 200 GET https://api.ejemplo.com/users (142ms)
```

### Sanitización de headers sensibles

Para redactar headers como `Authorization` o `X-Api-Key`, conecta `DefaultLogSanitizer` de `:security-core`:

```swift
let sanitizer = DefaultLogSanitizer()

let loggingObserver = LoggingObserver(
    logger: logger,
    headerSanitizer: { key, value in sanitizer.sanitize(key: key, value: value) }
)
```

Con sanitización, el output redacta valores sensibles:
```
[CoreDataPlatform] DEBUG: --> GET /users [Accept: application/json, Authorization: ██]
```

### Límites y buenas prácticas

- **El backend de logging lo defines tú.** El SDK nunca imprime nada por sí solo. `NetworkLogger.NOOP` es el default.
- **Siempre sanitiza en producción.** Usa `DefaultLogSanitizer` o tu propia implementación para evitar filtrar tokens o credenciales en logs.
- **Los observers son solo para observabilidad.** No uses `NetworkEventObserver` para lógica de negocio, transformación de datos, o side effects que afecten el flujo de la request.
- **Múltiples observers son posibles.** Puedes combinar `LoggingObserver` con tus propios observers de métricas o tracing:
  ```swift
  observers: [loggingObserver, metricsObserver, tracingObserver]
  ```

---

## Resumen: Clean Architecture con el SDK

| Capa | Responsabilidad | Conoce al SDK? |
|---|---|---|
| **Domain** (Protocolos + Use Cases) | Contratos, lógica de negocio | ❌ Puro Swift |
| **Data** (Adapter + SDK) | Conecta SDK con protocolos del domain | ✅ Importa `CoreDataPlatform` |
| **DI** (Container) | Ensambla las capas | ✅ Crea instancias concretas |

**Principios SOLID aplicados:**
- **S** — El adapter solo traduce entre el SDK y tu protocolo. No contiene lógica de negocio.
- **O** — El SDK es extensible (interceptors, observers) sin modificar código existente.
- **L** — El adapter es sustituible por cualquier implementación del protocolo (ej. mock para tests).
- **I** — Protocolos pequeños y enfocados: `UserRepositoryProtocol`, `CredentialProvider`, `SecretStore`.
- **D** — Los consumidores dependen de la abstracción (`UserRepositoryProtocol`), no del `UserRepository` concreto.

---

## Preguntas frecuentes

**¿Necesito configurar URLSession, Alamofire, o algo de networking?**
No. El SDK usa Ktor con el engine Darwin internamente. Tú solo usas repositories.

**¿Las funciones suspend de Kotlin funcionan con async/await en Swift?**
Sí. Kotlin/Native exporta funciones suspend como funciones async en Swift (a partir de Kotlin 1.7+).

**¿Puedo testear sin el SDK?**
Sí. Crea un mock que implemente tu protocolo:

```swift
final class MockUserRepository: UserRepositoryProtocol {
    func getUsers() async throws -> [User] {
        // Retorna datos de prueba
    }
    func getUser(id: Int64) async throws -> User {
        // Retorna dato de prueba
    }
}
```

**¿Dónde está la documentación completa?**
- `docs/integration-guide.md` — Guía completa de integración
- `docs/clean-architecture-integration.md` — Integración con Clean Architecture
- `network-core/README.md` — Contratos y pipeline de ejecución
- `security-core/README.md` — Credenciales, sesiones, almacenamiento seguro
