# Guía Rápida para Android

**Cómo usar el Core Data Platform SDK en un proyecto Android con Clean Architecture.**

---

## ¿Qué es esto?

Es un SDK interno que te da networking y seguridad listos para usar. En vez de configurar Retrofit/Ktor directamente, manejar errores manualmente, o escribir lógica de retry en cada pantalla, este SDK te da:

- **Llamadas HTTP** con retry automático, clasificación de errores, y timeouts configurables.
- **Manejo de credenciales** (Bearer token, API Key, Basic Auth) sin que tú toques headers.
- **Almacenamiento seguro** vía Android Keystore, encapsulado en `AndroidSecretStore`.
- **Un resultado tipado** (`NetworkResult<T>`) que es `Success` o `Failure` — nunca excepciones sueltas.

Tú solo interactúas con **repositorios** que devuelven modelos de dominio limpios. Todo lo demás (transporte HTTP, serialización, retry, headers de auth) sucede por debajo.

---

## Arquitectura: dónde encaja el SDK

```
┌─────────────────────────────────────────────────┐
│  Presentation (Compose + ViewModel)             │
│  • Solo conoce interfaces del domain layer      │
│  • Recibe dependencias vía constructor (DI)     │
├─────────────────────────────────────────────────┤
│  Domain (Use Cases + Interfaces)                │
│  • Define UserRepositoryContract                │
│  • Contiene lógica de negocio si aplica         │
│  • No importa el SDK directamente               │
├─────────────────────────────────────────────────┤
│  Data (SDK + Adapters)                          │
│  • SampleApiFactory.create() → UserRepository   │
│  • Adapter implementa UserRepositoryContract    │
│  • AndroidSecretStore para almacenamiento seguro│
├─────────────────────────────────────────────────┤
│  Core Data Platform SDK (dependencia Gradle)    │
│  • network-core, network-ktor, security-core    │
└─────────────────────────────────────────────────┘
```

**Principio clave:** tu ViewModel nunca sabe que el SDK existe. Solo conoce una interfaz que le provee datos.

---

## Ejemplo completo: integración del SDK

> **Nota:** Los ejemplos de esta guía usan `User` y `UserRepository` del módulo `:sample-api`, que es un **módulo piloto de referencia**. En tu proyecto real, sustituirás esto por tus propios módulos de dominio (ej. `:payments-api`, `:loyalty-api`) siguiendo el mismo patrón.

### Paso 1 — Capa Domain: define el contrato

Define una interfaz que abstraiga el repository. Las capas superiores (ViewModel, Use Cases) solo conocerán esta interfaz.

```kotlin
// domain/repository/UserRepositoryContract.kt

import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.sample.model.User

interface UserRepositoryContract {
    suspend fun getUsers(): NetworkResult<List<User>>
    suspend fun getUser(id: Long): NetworkResult<User>
}
```

> **Principio SOLID (D — Dependency Inversion):** El ViewModel depende de una abstracción (`UserRepositoryContract`), no del `UserRepository` concreto del SDK.

### Paso 2 — Capa Data: adapter que conecta el SDK con tu contrato

```kotlin
// data/repository/UserRepositoryAdapter.kt

import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.sample.model.User
import com.dancr.platform.sample.repository.UserRepository

class UserRepositoryAdapter(
    private val sdkRepository: UserRepository
) : UserRepositoryContract {

    override suspend fun getUsers(): NetworkResult<List<User>> =
        sdkRepository.getUsers()

    override suspend fun getUser(id: Long): NetworkResult<User> =
        sdkRepository.getUser(id)
}
```

> **Principio SOLID (S — Single Responsibility):** El adapter solo traduce entre el SDK y tu contrato. No contiene lógica de negocio ni de UI.

### Paso 3 — Inyección de dependencias

```kotlin
// di/DataModule.kt (Hilt)

import com.dancr.platform.sample.di.SampleApiFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideUserRepository(): UserRepositoryContract =
        UserRepositoryAdapter(SampleApiFactory.create())
}
```

Alternativa con Koin:

```kotlin
// di/dataModule.kt (Koin)

import com.dancr.platform.sample.di.SampleApiFactory
import org.koin.dsl.module

val dataModule = module {
    single<UserRepositoryContract> {
        UserRepositoryAdapter(SampleApiFactory.create())
    }
}
```

> **Principio SOLID (D — Dependency Inversion):** El módulo DI crea las implementaciones concretas, pero expone solo la interfaz. Toda la app depende de abstracciones.

### Paso 4 — Consumir desde la capa domain

Desde un Use Case, ViewModel, o cualquier componente de tu app, consume el contrato inyectado:

```kotlin
// El consumidor solo conoce la interfaz — no sabe que el SDK existe
repository.getUsers().fold(
    onSuccess = { users -> /* modelos de dominio limpios */ },
    onFailure = { error -> /* error.message es seguro para el usuario */ }
)
```

La capa de presentación (ViewModel, Compose, etc.) queda a criterio de tu arquitectura. Lo importante es que **nunca importe el SDK directamente** — solo el contrato definido en el Paso 1.

---

## ¿Qué es `NetworkResult`?

Es lo que te devuelve cualquier repository del SDK. Siempre es uno de dos:

```kotlin
// Éxito → tiene tus datos + metadata (status code, duración, etc.)
NetworkResult.Success(data = List<User>, metadata = ResponseMetadata)

// Error → tiene un error tipado con mensaje seguro para el usuario
NetworkResult.Failure(error = NetworkError.Connectivity)
```

### Cómo consumirlo

```kotlin
// Opción 1: fold (recomendado — cubre ambos casos)
result.fold(
    onSuccess = { users -> /* mostrar datos */ },
    onFailure = { error -> /* mostrar error */ }
)

// Opción 2: when
when (result) {
    is NetworkResult.Success -> result.data  // tus datos
    is NetworkResult.Failure -> result.error // el error tipado
}

// Opción 3: encadenar transformaciones
result
    .map { users -> users.filter { it.company != null } }
    .onSuccess { filteredUsers -> /* actualizar UI */ }
    .onFailure { error -> /* manejar error */ }

// Opción 4: extraer directamente (solo si no te importa el error)
val users: List<User>? = result.getOrNull()
```

---

## Tipos de error

Cuando algo falla, recibes un `NetworkError` tipado. Cada tipo tiene un `.message` seguro para mostrar al usuario:

| Error | ¿Cuándo pasa? | `.message` | ¿Qué hacer en la UI? |
|---|---|---|---|
| `Connectivity` | Sin internet / servidor inalcanzable | "Unable to reach the server" | Mostrar banner "Sin conexión" + botón retry |
| `Timeout` | El servidor tardó demasiado | "The request timed out" | Mostrar "Intenta de nuevo" |
| `Authentication` | Token inválido o expirado (401) | "Authentication required" | Navegar a login |
| `Authorization` | Sin permisos (403) | "Access denied" | Mostrar "No tienes acceso" |
| `ServerError` | Error del servidor (500+) | "Server error" | Mostrar "Estamos con problemas" |
| `Serialization` | El JSON no matchea el DTO | "Failed to process response data" | Log urgente — el API cambió |
| `Unknown` | Error inesperado | "An unexpected error occurred" | Mostrar error genérico |

### Manejo detallado (si lo necesitas)

```kotlin
result.onFailure { error ->
    when (error) {
        is NetworkError.Connectivity -> showRetryBanner()
        is NetworkError.Timeout      -> showRetryBanner()
        is NetworkError.Authentication -> navigateToLogin()
        is NetworkError.Authorization  -> showAccessDenied()
        is NetworkError.ServerError    -> showGenericError()
        else -> showGenericError()
    }
}
```

### Manejo simple (para la mayoría de pantallas)

```kotlin
result.onFailure { error ->
    showSnackbar(error.message)  // siempre es seguro para el usuario
}
```

---

## ¿Necesitas autenticación?

Usa `AndroidSecretStore` + `DefaultSessionController` + `DefaultCredentialProvider` para un flujo completo de sesión:

```kotlin
import com.dancr.platform.security.credential.DefaultCredentialProvider
import com.dancr.platform.security.session.DefaultSessionController
import com.dancr.platform.security.store.AndroidSecretStore

// 1. Configura el almacenamiento seguro (EncryptedSharedPreferences + Android Keystore)
val secretStore = AndroidSecretStore(context)

// 2. Crea el session controller con tu lógica de refresh
val sessionController = DefaultSessionController(
    store = secretStore,
    refreshTokenProvider = { refreshToken ->
        // Tu lógica para llamar al endpoint de refresh
        authService.refresh(refreshToken)
    }
)

// 3. Crea el credential provider
val credentialProvider = DefaultCredentialProvider(sessionController)

// 4. Pásalo al factory
val repository = SampleApiFactory.create(
    credentialProvider = credentialProvider
)
```

En tu módulo DI (Hilt):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides @Singleton
    fun provideSecretStore(@ApplicationContext context: Context): AndroidSecretStore =
        AndroidSecretStore(context)

    @Provides @Singleton
    fun provideSessionController(store: AndroidSecretStore): DefaultSessionController =
        DefaultSessionController(store = store)

    @Provides @Singleton
    fun provideCredentialProvider(controller: DefaultSessionController): DefaultCredentialProvider =
        DefaultCredentialProvider(controller)

    @Provides @Singleton
    fun provideUserRepository(credentialProvider: DefaultCredentialProvider): UserRepositoryContract =
        UserRepositoryAdapter(SampleApiFactory.create(credentialProvider = credentialProvider))
}
```

El SDK automáticamente:
- Llama a `credentialProvider.current()` antes de cada request
- Convierte el `Credential` al header correcto
- Si no hay sesión activa, la request va sin auth

### Refresh, invalidación y estado de sesión

```kotlin
// Consultar si hay sesión activa (derivado del estado, nunca estado duplicado)
if (sessionController.isAuthenticated) { /* ... */ }

// Refresh proactivo (ej. antes de que expire el token)
val outcome = sessionController.refreshSession()
when (outcome) {
    is RefreshOutcome.Refreshed  -> { /* nueva credencial activa */ }
    is RefreshOutcome.NotNeeded  -> { /* no había refresh token o provider — estado sin cambios */ }
    is RefreshOutcome.Failed     -> { /* falló — sesión expirada */ }
}

// Force-logout desde cualquier capa (ej. al recibir un 401)
sessionController.invalidate()  // limpia credenciales, emite Invalidated
// vs. endSession() que es logout intencional del usuario y emite Ended
```

> `credentialProvider.invalidate()` delega a `sessionController.invalidate()`. Útil cuando el auth interceptor detecta un 401 sin acceso directo al controller.

---

## ¿Necesitas configuración personalizada?

```kotlin
import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.config.RetryPolicy
import kotlin.time.Duration.Companion.seconds

val myConfig = NetworkConfig(
    baseUrl = "https://api.tuempresa.com/v1",
    defaultHeaders = mapOf(
        "Accept" to "application/json",
        "X-App-Version" to BuildConfig.VERSION_NAME
    ),
    connectTimeout = 10.seconds,
    readTimeout = 20.seconds,
    retryPolicy = RetryPolicy.ExponentialBackoff(
        maxRetries = 3,
        initialDelay = 1.seconds,
        maxDelay = 15.seconds
    )
)

val repository = SampleApiFactory.create(config = myConfig)
```

### Opciones de retry

```kotlin
RetryPolicy.None                                    // Sin reintentos
RetryPolicy.FixedDelay(maxRetries = 3, delay = 2.seconds)  // Espera fija
RetryPolicy.ExponentialBackoff(maxRetries = 3)       // 1s → 2s → 4s (default)
```

> **Nota:** Solo se reintentan errores de red y servidor (Connectivity, Timeout, ServerError). Un 401 o 403 **nunca** se reintenta automáticamente.

---

## Logging y observabilidad

El SDK incluye `LoggingObserver`, un observer que registra el ciclo de vida de cada request HTTP. **Por defecto es no-op** — no imprime nada a menos que tú le pases un `NetworkLogger`.

### Configuración básica

```kotlin
import com.dancr.platform.network.observability.LoggingObserver
import com.dancr.platform.network.observability.NetworkLogger

// 1. Define tu backend de logging (Timber, Logcat, etc.)
val logger = NetworkLogger { level, tag, message ->
    when (level) {
        NetworkLogger.Level.DEBUG -> Log.d(tag, message)
        NetworkLogger.Level.INFO  -> Log.i(tag, message)
        NetworkLogger.Level.WARN  -> Log.w(tag, message)
        NetworkLogger.Level.ERROR -> Log.e(tag, message)
    }
}

// 2. Crea el observer
val loggingObserver = LoggingObserver(logger = logger)

// 3. Pásalo al factory
val repository = SampleApiFactory.create(
    observers = listOf(loggingObserver)
)
```

Output de ejemplo:
```
D/CoreDataPlatform: --> GET https://api.ejemplo.com/users [Accept: application/json]
I/CoreDataPlatform: <-- 200 GET https://api.ejemplo.com/users (142ms)
```

### Sanitización de headers sensibles

Para redactar headers como `Authorization` o `X-Api-Key`, conecta `DefaultLogSanitizer` de `:security-core`:

```kotlin
import com.dancr.platform.security.sanitizer.DefaultLogSanitizer

val sanitizer = DefaultLogSanitizer()

val loggingObserver = LoggingObserver(
    logger = logger,
    headerSanitizer = { key, value -> sanitizer.sanitize(key, value) }
)
```

Con sanitización, el output redacta valores sensibles:
```
D/CoreDataPlatform: --> GET /users [Accept: application/json, Authorization: ██]
```

### Límites y buenas prácticas

- **El backend de logging lo defines tú.** El SDK nunca imprime nada por sí solo. `NetworkLogger.NOOP` es el default.
- **Siempre sanitiza en producción.** Usa `DefaultLogSanitizer` o tu propia implementación de `LogSanitizer` para evitar filtrar tokens o credenciales en logs.
- **Los observers son solo para observabilidad.** No uses `NetworkEventObserver` para lógica de negocio, transformación de datos, o side effects que afecten el flujo de la request.
- **Múltiples observers son posibles.** Puedes combinar `LoggingObserver` con tus propios observers de métricas o tracing:
  ```kotlin
  observers = listOf(loggingObserver, metricsObserver, tracingObserver)
  ```

---

## Diagrama: ¿qué pasa cuando llamas `repository.getUsers()`?

```
Tu código (solo conoce UserRepositoryContract)
    │
    │  repository.getUsers()
    ▼
UserRepositoryAdapter (capa data — implementa el contrato)
    │
    ▼
UserRepository (del SDK)
    │  llama dataSource.fetchUsers()
    │  mapea el resultado: .map(UserMapper::toDomain)
    ▼
UserRemoteDataSource
    │  construye: HttpRequest(path = "/users", method = GET)
    │  delega a SafeRequestExecutor
    ▼
DefaultSafeRequestExecutor  ← (aquí pasa todo lo importante)
    │
    ├── 1. Agrega headers por defecto + headers de auth
    ├── 2. Ejecuta la request HTTP via Ktor
    ├── 3. Si falla por red → clasifica el error → reintenta si aplica
    ├── 4. Si respuesta OK → deserializa JSON → List<UserDto>
    └── 5. Si respuesta error → clasifica (401? 500? timeout?)
    │
    ▼
NetworkResult<List<User>>  ← esto es lo que recibe tu código
```

**Lo importante:** tu código solo ve la interfaz `UserRepositoryContract` y el resultado `NetworkResult<User>`. Todo lo del medio es transparente.

---

## Resumen: Clean Architecture con el SDK

| Capa | Responsabilidad | Conoce al SDK? |
|---|---|---|
| **Domain** (Interfaces + Use Cases) | Contratos, lógica de negocio | ❌ Puro Kotlin |
| **Data** (Adapter + SDK) | Conecta SDK con interfaces del domain | ✅ Importa SDK |
| **DI** (Hilt / Koin module) | Ensambla las capas | ✅ Crea instancias concretas |

**Principios SOLID aplicados:**
- **S** — El adapter solo traduce entre el SDK y tu contrato. No contiene lógica de negocio.
- **O** — El SDK es extensible (interceptors, observers) sin modificar código existente.
- **L** — El adapter es sustituible por cualquier implementación del contrato (ej. mock para tests).
- **I** — Interfaces pequeñas y enfocadas: `UserRepositoryContract`, `CredentialProvider`, `SecretStore`.
- **D** — Los consumidores dependen de la abstracción (`UserRepositoryContract`), no del `UserRepository` concreto.

### Referencia rápida

| Quiero... | Hago... |
|---|---|
| Obtener datos | `repository.getUsers()` → devuelve `NetworkResult<List<User>>` |
| Manejar éxito/error | `.fold(onSuccess = { }, onFailure = { })` |
| Mostrar error al usuario | `error.message` (siempre seguro) |
| Agregar autenticación | Configurar `AndroidSecretStore` + `DefaultSessionController` + `DefaultCredentialProvider` |
| Cambiar URL/timeouts | Crear un `NetworkConfig` y pasarlo al factory |
| Transformar datos | `.map { }` sobre el `NetworkResult` |
| Encadenar llamadas | `.flatMap { }` para llamadas secuenciales |
| Activar logging | Crear `LoggingObserver(logger = ...)` y pasarlo como observer al factory |

---

## Preguntas frecuentes

**¿Necesito configurar Ktor, Retrofit, o algo de networking?**
No. El SDK lo maneja internamente. Tú solo usas repositories.

**¿Necesito hacer try-catch?**
No. `NetworkResult` captura todos los errores. Nunca te llega una excepción.

**¿Qué pasa si no hay internet?**
Recibes `NetworkResult.Failure(NetworkError.Connectivity(...))`. El SDK ya intentó reintentar si tenías retry configurado.

**¿Funciona con Hilt/Koin?**
Sí. Registra el adapter como implementación del contrato:
```kotlin
// Hilt
@Provides @Singleton
fun provideUserRepository(): UserRepositoryContract =
    UserRepositoryAdapter(SampleApiFactory.create())

// Koin
single<UserRepositoryContract> { UserRepositoryAdapter(SampleApiFactory.create()) }
```

**¿Puedo testear sin el SDK?**
Sí. Crea un mock que implemente tu contrato:
```kotlin
class FakeUserRepository : UserRepositoryContract {
    override suspend fun getUsers() = NetworkResult.Success(
        data = listOf(User(1, "Test", "test", "test@mail.com", null)),
        metadata = ResponseMetadata(statusCode = 200, durationMs = 10, attemptCount = 1)
    )
    override suspend fun getUser(id: Long) = TODO()
}
```

**¿Puedo ver los logs de las requests?**
Sí. Crea un `LoggingObserver` con tu `NetworkLogger` y pásalo al factory. Ver la sección [Logging y observabilidad](#logging-y-observabilidad).

**¿Dónde está la documentación completa?**
- `docs/integration-guide.md` — Guía completa de integración
- `network-core/README.md` — Contratos y pipeline de ejecución
- `security-core/README.md` — Credenciales, sesiones, almacenamiento seguro
- `docs/diagrams/` — Diagramas de arquitectura (SVG)
