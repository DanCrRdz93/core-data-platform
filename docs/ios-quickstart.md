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
│  Core Data Platform SDK (Maven Central / KMP)   │
│  io.github.dancrrdz93:network-core:0.1.0       │
│  io.github.dancrrdz93:network-ktor:0.1.0       │
│  io.github.dancrrdz93:security-core:0.1.0      │
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

### Certificate Pinning

Protege contra ataques MITM fijando los certificados esperados del servidor:

```swift
import CoreDataPlatform

// 1. Define los pins (SHA-256 del certificado DER, codificado en base64)
let trustPolicy = DefaultTrustPolicy(
    pins: [
        "api.tuempresa.com": Set([
            CertificatePin(algorithm: "sha256", hash: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
            CertificatePin(algorithm: "sha256", hash: "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")  // backup
        ])
    ]
)

// 2. Crea el engine con pinning habilitado
let engine = KtorHttpEngine.companion.create(config: myConfig, trustPolicy: trustPolicy)

// 3. La conexión se rechaza si ningún certificado del servidor coincide con los pins.
```

> **Importante:** Siempre incluye al menos un pin de respaldo. Si el certificado principal rota y no tienes backup, la app no podrá conectarse.
>
> En iOS, el pinning usa `handleChallenge` de NSURLSession con evaluación de `SecTrust` internamente.

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

### General

**¿Necesito configurar URLSession, Alamofire, o algo de networking?**
No. El SDK usa Ktor con el engine Darwin internamente. Tú solo usas repositories.

**¿Las funciones suspend de Kotlin funcionan con async/await en Swift?**
Sí. Kotlin/Native exporta funciones suspend como funciones async en Swift (a partir de Kotlin 1.7+).

**¿Puedo testear sin el SDK?**
Sí. Crea un mock que implemente tu protocolo:

```swift
final class MockUserRepository: UserRepositoryProtocol {
    func getUsers() async throws -> [User] {
        return [User(id: 1, name: "Test", username: "test", email: "test@mail.com", company: nil)]
    }
    func getUser(id: Int64) async throws -> User {
        return User(id: id, name: "Test", username: "test", email: "test@mail.com", company: nil)
    }
}
```

**¿Puedo ver los logs de las requests?**
Sí. Crea un `LoggingObserver` con tu `NetworkLogger` y pásalo al factory. Ver la sección [Logging y observabilidad](#logging-y-observabilidad).

**¿El SDK funciona con SwiftUI?**
Sí. El SDK no tiene dependencia de ningún framework de UI. Los repositorios devuelven datos vía `async/await`, que es compatible nativamente con SwiftUI:
```swift
struct UsersView: View {
    @StateObject private var viewModel = UsersViewModel()

    var body: some View {
        List(viewModel.users, id: \.id) { user in
            Text(user.name)
        }
        .task { await viewModel.loadUsers() }
    }
}
```

**¿Puedo usar Combine con el SDK?**
Las funciones del SDK son `async`, no publican vía Combine directamente. Sin embargo, puedes bridgear fácilmente:
```swift
import Combine

func usersPublisher() -> AnyPublisher<[User], AppNetworkError> {
    Future { promise in
        Task {
            do {
                let users = try await repository.getUsers()
                promise(.success(users))
            } catch let error as AppNetworkError {
                promise(.failure(error))
            }
        }
    }
    .eraseToAnyPublisher()
}
```

---

### Seguridad

**¿Cómo se almacenan las credenciales de forma segura en iOS?**
`IosSecretStore` usa **Keychain Services** de iOS. Los datos se almacenan cifrados por el Secure Enclave del dispositivo. La configuración por defecto usa `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, lo que significa que:
- Los datos solo son accesibles cuando el dispositivo está desbloqueado.
- No se migran a otros dispositivos (ni vía backup ni vía transferencia).
- Están protegidos por hardware (Secure Enclave en dispositivos con chip A7+).

**¿Qué niveles de accesibilidad del Keychain están disponibles?**
`KeychainConfig` soporta los siguientes niveles vía `KeychainAccessibility`:

| Nivel | Descripción | ¿En backups? |
|---|---|---|
| `WHEN_UNLOCKED` | Accesible solo con dispositivo desbloqueado | Sí |
| `AFTER_FIRST_UNLOCK` | Accesible después del primer desbloqueo tras reinicio | Sí |
| `WHEN_PASSCODE_SET_THIS_DEVICE_ONLY` | Requiere passcode activo; este dispositivo solamente | No |
| `WHEN_UNLOCKED_THIS_DEVICE_ONLY` | Desbloqueado + este dispositivo solamente | No |
| `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY` | Primer desbloqueo + este dispositivo solamente | No |

Recomendación: usa `WHEN_UNLOCKED_THIS_DEVICE_ONLY` o `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY` para credenciales sensibles.

```swift
let secretStore = IosSecretStore(config: KeychainConfig(
    serviceName: "com.tuapp.secrets",
    accessibility: .whenUnlockedThisDeviceOnly
))
```

**¿El SDK protege contra ataques Man-in-the-Middle (MITM)?**
Sí, en dos niveles:
1. **HTTPS obligatorio** — `NetworkConfig` rechaza URLs `http://` por defecto. Solo se permite HTTP con `allowInsecureConnections = true` (para desarrollo local).
2. **Certificate Pinning** — Puedes configurar `DefaultTrustPolicy` con pins SHA-256 de tus certificados. En iOS se usa `handleChallenge` de `NSURLSession` con evaluación de `SecTrust` internamente. La conexión se rechaza si ningún certificado del servidor coincide.

**¿Las credenciales pueden filtrarse en logs o crash reports?**
No. El SDK implementa múltiples capas de protección alineadas a OWASP MASVS:
- `Credential.toString()` redacta valores sensibles con `██` (ej. `Bearer(token=██)`).
- `HttpRequest.toString()` solo muestra keys de headers, nunca valores.
- `RawResponse.toString()` muestra tamaño del body, no su contenido.
- `SessionCredentials.toString()` redacta tanto la credencial como el refresh token.
- `LoggingObserver` usa `REDACT_ALL` por defecto — todos los valores de headers se reemplazan con `██`.

**¿Las credenciales del SDK se incluyen en backups de iCloud?**
Depende de la accesibilidad configurada en `KeychainConfig`:
- Los niveles `*_THIS_DEVICE_ONLY` **nunca** se incluyen en backups — ni en iCloud ni en iTunes.
- Los niveles sin `THIS_DEVICE_ONLY` se incluyen en backups cifrados de iCloud pero **no** en backups no cifrados.

Para máxima seguridad, usa `WHEN_UNLOCKED_THIS_DEVICE_ONLY` o `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`.

**¿Qué pasa si el usuario hace jailbreak al dispositivo?**
El SDK no detecta jailbreak. Sin embargo, los datos del Keychain están protegidos por el Secure Enclave, que funciona independientemente del estado del OS. Si necesitas detección de jailbreak, agrégala en tu capa de app y llama a `sessionController.invalidate()` si detectas compromiso.

**¿Qué pasa si un certificado del servidor rota y tengo pinning activo?**
La app no podrá conectarse hasta que actualices los pins. Por eso es **obligatorio** incluir al menos un pin de respaldo (backup pin) en tu `DefaultTrustPolicy`. Cuando rotas un certificado, primero despliega una versión de la app con el nuevo pin como backup, luego rota el certificado en el servidor.

**¿Puedo desactivar certificate pinning para debugging?**
Sí. Simplemente no pases un `TrustPolicy` al crear el engine:
```swift
// Sin pinning (dev/debug)
let engine = KtorHttpEngine.companion.create(config: myConfig, trustPolicy: nil)

// Con pinning (producción)
let engine = KtorHttpEngine.companion.create(config: myConfig, trustPolicy: myTrustPolicy)
```

**¿El SDK es compatible con App Transport Security (ATS)?**
Sí. El SDK usa HTTPS por defecto, lo cual cumple con ATS. No necesitas excepciones en `Info.plist` para el SDK. Solo si usas `allowInsecureConnections = true` para desarrollo local, necesitarás una excepción ATS para tu dominio local:
```xml
<!-- Info.plist — SOLO para desarrollo local -->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSExceptionDomains</key>
    <dict>
        <key>localhost</key>
        <dict>
            <key>NSExceptionAllowsInsecureHTTPLoads</key>
            <true/>
        </dict>
    </dict>
</dict>
```

**¿Cómo invalido la sesión si detecto una vulnerabilidad en runtime?**
Llama a `sessionController.invalidate()` desde cualquier parte de tu app. Esto:
1. Cambia el estado a `SessionState.Idle`.
2. Emite `SessionEvent.Invalidated`.
3. Borra las credenciales de `SecretStore` (Keychain).

```swift
// Ejemplo: invalidar si detectas compromiso
if integrityCheck.isCompromised() {
    try await sessionController.invalidate()
    navigateToLogin()
}
```

---

### Implementación

**¿Cómo manejo los tipos de Kotlin en Swift?**
Kotlin/Native exporta los tipos a Objective-C, que Swift consume. Tabla de equivalencias comunes:

| Kotlin | Swift |
|---|---|
| `Long` | `Int64` |
| `Int` | `Int32` |
| `String` | `String` |
| `Boolean` | `Bool` (con `KotlinBoolean` en algunos contextos) |
| `List<T>` | `[T]` (como `NSArray` internamente) |
| `Map<K,V>` | `[K: V]` (como `NSDictionary`) |
| `sealed class` | Clase base con subclases — usa `is` para pattern matching |
| `suspend fun` | `async throws` en Swift |

**¿Los sealed class de Kotlin funcionan bien en Swift?**
Funcionan, pero el compilador Swift no verifica exhaustividad. Siempre agrega un `default` en tus `switch`:
```swift
switch result {
case let success as NetworkResultSuccess<AnyObject>:
    // manejar éxito
case let failure as NetworkResultFailure:
    // manejar error
default:
    // caso de seguridad
    break
}
```

**¿Puedo usar múltiples APIs con diferentes URLs base?**
Sí. Crea un `NetworkConfig` y un executor separado para cada API:
```swift
let mainConfig = NetworkConfig(baseUrl: "https://api.tuempresa.com/v1", ...)
let authConfig = NetworkConfig(baseUrl: "https://auth.tuempresa.com/v1", ...)
// Crea factories/engines separados para cada config
```

**¿El SDK maneja automáticamente el refresh de tokens?**
El SDK llama a `credentialProvider.current()` antes de cada request. Si usas `DefaultCredentialProvider` + `DefaultSessionController`, el refresh se puede disparar proactivamente con `sessionController.refreshSession()`. Sin embargo, el SDK **no** hace refresh automático al recibir un 401 — eso es responsabilidad de tu capa de app. Patrón recomendado:
```swift
do {
    let users = try await repository.getUsers()
} catch AppNetworkError.authentication {
    let outcome = try await sessionController.refreshSession()
    if outcome is RefreshOutcome.Refreshed {
        // Re-ejecutar la request original
    } else {
        navigateToLogin()
    }
}
```

**¿Puedo cancelar una request en curso?**
Sí. Cancela la `Task` de Swift:
```swift
let task = Task {
    let users = try await repository.getUsers()
    // ...
}
// Después:
task.cancel()  // la request se cancela, recibes un error de cancelación
```

**¿Cómo manejo la concurrencia con el SDK?**
Las funciones del SDK son `async` y thread-safe. Puedes llamarlas desde cualquier actor o Task. Si usas `@MainActor` en tu ViewModel, las llamadas al SDK se ejecutarán en un hilo de background automáticamente y el resultado volverá al main thread:
```swift
@MainActor
class UsersViewModel: ObservableObject {
    @Published var users: [User] = []

    func loadUsers() async {
        do {
            users = try await repository.getUsers()  // async en background, resultado en main
        } catch {
            // manejar error
        }
    }
}
```

**¿Puedo agregar interceptors custom desde Swift?**
Los interceptors se configuran en Kotlin al crear el executor. Desde Swift, pásalos al factory o crea subclases Kotlin que implementen `RequestInterceptor`/`ResponseInterceptor`. La configuración típica se hace en la capa de DI (Kotlin side) antes de exportar el framework.

**¿Cómo hago requests POST con body JSON desde Swift?**
Serializa tu objeto a JSON data y pásalo como body al construir el `HttpRequest` desde Kotlin. El patrón recomendado es que el data source (Kotlin) maneje la serialización internamente — desde Swift solo llamas métodos de alto nivel:
```swift
// Desde Swift, llamas al repository (alto nivel)
let order = try await orderRepository.createOrder(item: "abc", quantity: 2)

// La serialización JSON ocurre internamente en el DataSource (Kotlin)
```

---

### Configuración

**¿Puedo usar HTTP para desarrollo local?**
Sí, pero debe ser explícito:
```swift
let devConfig = NetworkConfig(
    baseUrl: "http://localhost:8080",
    defaultHeaders: [:],
    connectTimeout: KotlinDuration.companion.seconds(30),
    readTimeout: KotlinDuration.companion.seconds(30),
    writeTimeout: KotlinDuration.companion.seconds(30),
    retryPolicy: RetryPolicy.None.shared,
    allowInsecureConnections: true  // ⚠️ SOLO para desarrollo local
)
```
Nunca actives `allowInsecureConnections` en producción. El SDK lanzará un error si usas `http://` sin este flag.

**¿Cómo configuro diferentes ambientes (dev/staging/prod)?**
Define configuraciones por entorno usando un enum o scheme de Xcode:
```swift
enum Environment {
    case development, staging, production

    var config: NetworkConfig {
        switch self {
        case .development:
            return NetworkConfig(baseUrl: "https://dev-api.com/v1", ...)
        case .staging:
            return NetworkConfig(baseUrl: "https://staging-api.com/v1", ...)
        case .production:
            return NetworkConfig(baseUrl: "https://api.com/v1", ...)
        }
    }
}

// Selección basada en build configuration
#if DEBUG
let environment = Environment.development
#else
let environment = Environment.production
#endif
```

**¿Cuáles son los timeouts por defecto?**
| Parámetro | Default |
|---|---|
| `connectTimeout` | 30 segundos |
| `readTimeout` | 30 segundos |
| `writeTimeout` | 30 segundos |

Ajústalos según tu API. Para APIs lentas (reportes, uploads), considera timeouts más altos. Para APIs rápidas (lookups), usa timeouts más cortos para fallar rápido.

**¿Qué errores se reintentan automáticamente?**
Solo los que tienen `isRetryable = true`:

| Error | ¿Reintentable? | Razón |
|---|---|---|
| `Connectivity` | ✅ Sí | Puede ser transitorio (red intermitente) |
| `Timeout` | ✅ Sí | El servidor pudo estar ocupado temporalmente |
| `ServerError` (5xx) | ✅ Sí | El servidor puede recuperarse |
| `Authentication` (401) | ❌ No | Requiere intervención del usuario o refresh |
| `Authorization` (403) | ❌ No | El usuario no tiene permisos |
| `ClientError` (4xx) | ❌ No | La request es inválida |
| `Serialization` | ❌ No | Desajuste de contrato (no transitorio) |
| `Cancelled` | ❌ No | Acción intencional del usuario |

**¿Las opciones de `RetryPolicy` son las mismas que en Android?**
Sí. El SDK es Kotlin Multiplatform — la lógica de retry es idéntica en ambas plataformas:
```swift
RetryPolicy.None.shared                                       // Sin reintentos
RetryPolicy.FixedDelay(maxRetries: 3, delay: ...)             // Espera fija
RetryPolicy.ExponentialBackoff(maxRetries: 3, ...)            // 1s → 2s → 4s
```

---

### Vulnerabilidades y hardening

**¿Qué pasa si alguien extrae el binario de la app y busca API keys?**
Las API keys hardcodeadas son siempre vulnerables a ingeniería inversa (ej. con `strings`, Hopper, o class-dump). El SDK mitiga esto:
1. **`IosSecretStore`** almacena credenciales en Keychain — no en el binario.
2. **`Credential.toString()` redacta valores** — no aparecen en dumps de memoria.
3. Para keys que deben estar en la app, usa Xcode build configurations y no las incluyas en repositorios públicos.

La mejor práctica es que las API keys sensibles se obtengan vía un endpoint autenticado, no embebidas en el binario.

**¿El SDK sanitiza datos sensibles en los logs de métricas y trazas?**
Sí. Además del `LoggingObserver`:
- `MetricsObserver` y `TracingObserver` usan `sanitizePath()` para strip query parameters de los paths antes de emitir tags (evita filtrar IDs, tokens en query strings).
- `NetworkLogger.NOOP` es el default — el SDK no imprime nada a menos que tú configures un logger.

**¿Cómo evito que información interna de `diagnostic` se muestre al usuario?**
Nunca muestres `diagnostic` en la UI. Usa siempre el `errorDescription` de tu `AppNetworkError`:
```swift
// ✅ SEGURO — errorDescription es user-facing
showAlert(error.localizedDescription)  // "No se pudo conectar al servidor"

// ❌ INSEGURO — diagnostic contiene stack traces e info interna
// No expongas el diagnostic de NetworkError al usuario
```

**¿El SDK protege contra replay attacks?**
El SDK no implementa protección anti-replay directamente (eso es responsabilidad del backend). Sin embargo, puedes agregar un nonce o timestamp vía un `RequestInterceptor` configurado en Kotlin.

**¿El SDK es vulnerable a SSL stripping?**
No, si usas la configuración por defecto. `NetworkConfig` rechaza URLs `http://` por defecto. Además, ATS de iOS bloquea conexiones HTTP por defecto. Si configuras `DefaultTrustPolicy` con certificate pinning, un atacante no podría interceptar la conexión ni con un certificado CA falso.

**¿Qué pasa si hay un proxy de debugging (Charles/Proxyman) en la red?**
Sin certificate pinning, un proxy con certificado CA instalado en el dispositivo puede interceptar tráfico HTTPS. Con certificate pinning (`DefaultTrustPolicy`), la conexión se rechaza porque el certificado del proxy no coincide con los pins configurados. Esto es el comportamiento deseado en producción.

Para debugging con proxy durante desarrollo, no configures `TrustPolicy` en tus builds de debug.

---

### Interoperabilidad Kotlin/Swift

**¿Necesito hacer algo especial para que KMP funcione en iOS?**
El framework KMP se genera con Gradle y se agrega a Xcode como framework estático. Asegúrate de:
1. Ejecutar `./gradlew :shared:assembleCoreDataPlatformReleaseXCFramework` (o similar) después de cambios en Kotlin.
2. Que el framework esté agregado en **General → Frameworks, Libraries, and Embedded Content**.
3. Importar `CoreDataPlatform` en tus archivos Swift.

**¿Los `Flow` y `StateFlow` de Kotlin funcionan en Swift?**
`StateFlow` y `Flow` se exportan como tipos Kotlin, pero no son directamente observables en Swift. Para observar el estado de sesión, por ejemplo, necesitas un wrapper:
```swift
// Wrapper para observar StateFlow desde Swift
func observeSessionState() -> AsyncStream<SessionState> {
    AsyncStream { continuation in
        Task {
            for await state in sessionController.state {
                continuation.yield(state)
            }
        }
    }
}
```

**¿Hay problemas conocidos con la interop Kotlin/Swift?**
Algunos puntos a tener en cuenta:
- Los `sealed class` no son exhaustivos en `switch` de Swift — siempre agrega `default`.
- `Long` de Kotlin es `Int64` en Swift, no `Int`.
- Los `companion object` se acceden con `.companion` o `.shared` (ej. `KtorHttpEngine.companion.create(...)`).
- Los genéricos de Kotlin se borran en Objective-C — `NetworkResult<User>` se exporta como `NetworkResult<AnyObject>`.
- Las excepciones de Kotlin que no son `CancellationException` se convierten en `NSError`.

---

### Documentación

**¿Dónde está la documentación completa?**
- `docs/integration-guide.md` — Guía completa de integración paso a paso
- `docs/security-checklist.md` — Checklist OWASP MASVS con todas las protecciones
- `docs/clean-architecture-integration.md` — Integración con Clean Architecture
- `network-core/README.md` — Contratos y pipeline de ejecución
- `security-core/README.md` — Credenciales, sesiones, almacenamiento seguro
- `docs/diagrams/` — Diagramas de arquitectura (SVG)
