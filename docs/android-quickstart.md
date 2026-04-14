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

### Certificate Pinning

Protege contra ataques MITM fijando los certificados esperados del servidor:

```kotlin
import com.dancr.platform.security.trust.DefaultTrustPolicy
import com.dancr.platform.security.trust.CertificatePin
import com.dancr.platform.network.ktor.KtorHttpEngine

// 1. Define los pins (SHA-256 del certificado DER, codificado en base64)
val trustPolicy = DefaultTrustPolicy(
    pins = mapOf(
        "api.tuempresa.com" to setOf(
            CertificatePin(algorithm = "sha256", hash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
            CertificatePin(algorithm = "sha256", hash = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")  // backup
        )
    )
)

// 2. Crea el engine con pinning habilitado
val engine = KtorHttpEngine.create(config = myConfig, trustPolicy = trustPolicy)

// 3. Úsalo en el executor o factory
// La conexión se rechaza si ningún certificado del servidor coincide con los pins.
```

> **Importante:** Siempre incluye al menos un pin de respaldo. Si el certificado principal rota y no tienes backup, la app no podrá conectarse.
>
> En Android, el pinning usa `OkHttp CertificatePinner` internamente. El formato del hash es `sha256/<base64>`.

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

### General

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

---

### Seguridad

**¿Cómo se almacenan las credenciales de forma segura en Android?**
`AndroidSecretStore` usa `EncryptedSharedPreferences` respaldado por **Android Keystore**. Esto significa que los datos se cifran con AES-256-GCM y las claves criptográficas están protegidas por hardware (TEE/StrongBox cuando está disponible). Nunca se almacena nada en texto plano.

**¿El SDK protege contra ataques Man-in-the-Middle (MITM)?**
Sí, en dos niveles:
1. **HTTPS obligatorio** — `NetworkConfig` rechaza URLs `http://` por defecto. Solo se permite HTTP con `allowInsecureConnections = true` (para desarrollo local).
2. **Certificate Pinning** — Puedes configurar `DefaultTrustPolicy` con pins SHA-256 de tus certificados. En Android se usa `OkHttp CertificatePinner` internamente. La conexión se rechaza si ningún certificado del servidor coincide.

**¿Las credenciales pueden filtrarse en logs o crash reports?**
No. El SDK implementa múltiples capas de protección alineadas a OWASP MASVS:
- `Credential.toString()` redacta valores sensibles con `██` (ej. `Bearer(token=██)`).
- `HttpRequest.toString()` solo muestra keys de headers, nunca valores.
- `RawResponse.toString()` muestra tamaño del body, no su contenido.
- `SessionCredentials.toString()` redacta tanto la credencial como el refresh token.
- `LoggingObserver` usa `REDACT_ALL` por defecto — todos los valores de headers se reemplazan con `██`.

**¿Qué pasa si el usuario rootea el dispositivo?**
El SDK no detecta root. Sin embargo, las credenciales almacenadas en Android Keystore están protegidas por hardware incluso en dispositivos rooteados. Las claves son **non-exportable** — el sistema las usa internamente pero nunca las expone. Si necesitas detección de root, agrégala en tu capa de app (ej. SafetyNet/Play Integrity) y usa `sessionController.invalidate()` si detectas compromiso.

**¿Los datos del SDK se incluyen en backups de Android?**
`EncryptedSharedPreferences` cifra todo su contenido, por lo que un backup contendría datos cifrados ilegibles sin acceso al Keystore del dispositivo original. Para máxima seguridad, puedes excluir el archivo de backups en tu `AndroidManifest.xml` con `android:fullBackupContent`.

**¿Qué pasa si un certificado del servidor rota y tengo pinning activo?**
La app no podrá conectarse hasta que actualices los pins. Por eso es **obligatorio** incluir al menos un pin de respaldo (backup pin) en tu `DefaultTrustPolicy`. Cuando rotas un certificado, primero despliega una versión de la app con el nuevo pin como backup, luego rota el certificado en el servidor.

**¿Puedo desactivar certificate pinning para debugging?**
Sí. Simplemente no pases un `TrustPolicy` al crear el engine:
```kotlin
// Sin pinning (dev/debug)
val engine = KtorHttpEngine.create(config)

// Con pinning (producción)
val engine = KtorHttpEngine.create(config, trustPolicy = myTrustPolicy)
```
No uses el flag `allowInsecureConnections` para esto — ese flag desactiva la validación HTTPS completa y solo debe usarse para `localhost`.

**¿Necesito configurar Network Security Config de Android?**
No para el SDK en sí — el SDK impone HTTPS por defecto en `NetworkConfig`. Sin embargo, si usas `allowInsecureConnections = true` para desarrollo local y tu `targetSdk >= 28`, necesitarás un `network_security_config.xml` que permita cleartext para `localhost`/`10.0.2.2`:
```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

**¿Cómo invalido la sesión si detecto una vulnerabilidad en runtime?**
Llama a `sessionController.invalidate()` desde cualquier parte de tu app. Esto:
1. Cambia el estado a `SessionState.Idle`.
2. Emite `SessionEvent.Invalidated`.
3. Borra las credenciales de `SecretStore`.

```kotlin
// Ejemplo: invalidar si detectas tamper
if (integrityCheck.isTampered()) {
    sessionController.invalidate()
    navigateToLogin()
}
```

---

### Implementación

**¿Puedo usar múltiples APIs con diferentes URLs base?**
Sí. Crea un `NetworkConfig` y un `DefaultSafeRequestExecutor` separado para cada API. Nunca compartas un executor entre APIs con diferente `baseUrl`:
```kotlin
val mainApi = NetworkConfig(baseUrl = "https://api.tuempresa.com/v1", ...)
val authApi = NetworkConfig(baseUrl = "https://auth.tuempresa.com/v1", ...)

val mainExecutor = DefaultSafeRequestExecutor(engine = KtorHttpEngine.create(mainApi), config = mainApi, ...)
val authExecutor = DefaultSafeRequestExecutor(engine = KtorHttpEngine.create(authApi), config = authApi, ...)
```

**¿Cómo agrego headers custom a una request específica?**
Agrega headers en el `HttpRequest`. Estos se combinan con los `defaultHeaders` de `NetworkConfig` (los del request tienen prioridad):
```kotlin
val request = HttpRequest(
    path = "/users",
    method = HttpMethod.GET,
    headers = mapOf("X-Custom-Header" to "valor")
)
```

**¿Cómo implemento paginación?**
Construye la request con query parameters para la página:
```kotlin
suspend fun fetchUsers(page: Int, size: Int): NetworkResult<List<UserDto>> = execute(
    request = HttpRequest(
        path = "/users?page=$page&size=$size",
        method = HttpMethod.GET
    ),
    deserialize = { json.decodeFromString(it.body!!.decodeToString()) }
)
```
En el repository, acumula resultados o usa `flatMap` para encadenar páginas.

**¿Qué pasa si el servidor retorna 204 No Content (body vacío)?**
El force-unwrap `response.body!!` fallará y recibirás `NetworkError.Serialization`. Protege contra bodies nulos:
```kotlin
deserialize = { response ->
    val body = response.body?.decodeToString()
        ?: return@execute Unit  // o tu tipo vacío
    json.decodeFromString(body)
}
```

**¿Puedo cancelar una request en curso?**
Sí. Cancela la coroutine o el `Job` que la lanzó. Ktor propaga la cancelación correctamente y recibirás `NetworkError.Cancelled`:
```kotlin
val job = viewModelScope.launch {
    repository.getUsers().fold(
        onSuccess = { /* ... */ },
        onFailure = { /* ... */ }
    )
}
// Después:
job.cancel()  // la request se cancela, recibes NetworkError.Cancelled
```

**¿El SDK maneja automáticamente el refresh de tokens?**
El SDK llama a `credentialProvider.current()` antes de cada request. Si usas `DefaultCredentialProvider` + `DefaultSessionController`, el refresh se puede disparar proactivamente con `sessionController.refreshSession()`. Sin embargo, el SDK **no** hace refresh automático al recibir un 401 — eso es responsabilidad de tu capa de app. Patrón recomendado:
```kotlin
result.onFailure { error ->
    if (error is NetworkError.Authentication) {
        val outcome = sessionController.refreshSession()
        if (outcome is RefreshOutcome.Refreshed) {
            // Re-ejecutar la request original
        } else {
            navigateToLogin()
        }
    }
}
```

**¿Puedo agregar interceptors custom?**
Sí. Implementa `RequestInterceptor` (pre-transport) o `ResponseInterceptor` (post-transport):
```kotlin
// Request interceptor: agrega un trace ID a cada request
val tracingInterceptor = RequestInterceptor { request, context ->
    request.copy(headers = request.headers + ("X-Trace-Id" to UUID.randomUUID().toString()))
}

// Response interceptor: extrae un header de la respuesta
val headerExtractor = ResponseInterceptor { response, request, context ->
    val rateLimitRemaining = response.headers["X-RateLimit-Remaining"]
    // guardar para uso posterior
    response
}
```

**¿Puedo hacer requests POST con body JSON?**
Sí. Serializa tu objeto a JSON y pásalo como `body`:
```kotlin
val orderJson = json.encodeToString(CreateOrderRequest(item = "abc", quantity = 2))
val request = HttpRequest(
    path = "/orders",
    method = HttpMethod.POST,
    headers = mapOf("Content-Type" to "application/json"),
    body = orderJson.encodeToByteArray()
)
```

**¿Cuántas instancias de `KtorHttpEngine` debería crear?**
Una por `NetworkConfig` (URL base). Crea el engine al inicio de la app, regístralo como singleton en tu DI, y compártelo entre todos los data sources de esa API. Llama a `engine.close()` al cerrar la app.

**¿`ignoreUnknownKeys = true` es seguro? ¿No oculta problemas?**
Es la práctica recomendada para resiliencia ante evolución del API. Si el backend agrega un campo nuevo, tu app no se rompe. Los campos que **faltan** en tu DTO sí causan error — eso se detecta como `NetworkError.Serialization` y es una señal crítica (desajuste de contrato).

---

### Configuración

**¿Puedo usar HTTP para desarrollo local?**
Sí, pero debe ser explícito:
```kotlin
val devConfig = NetworkConfig(
    baseUrl = "http://10.0.2.2:8080",  // emulador Android → localhost del host
    allowInsecureConnections = true     // ⚠️ SOLO para desarrollo local
)
```
Nunca actives `allowInsecureConnections` en producción. El SDK lanzará `IllegalArgumentException` si usas `http://` sin este flag.

**¿Cómo configuro diferentes ambientes (dev/staging/prod)?**
Define objetos de configuración por entorno y selecciona en tiempo de compilación:
```kotlin
val config = when (BuildConfig.FLAVOR) {
    "dev"     -> NetworkConfig(baseUrl = "https://dev-api.com/v1", retryPolicy = RetryPolicy.None)
    "staging" -> NetworkConfig(baseUrl = "https://staging-api.com/v1", retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 2))
    else      -> NetworkConfig(baseUrl = "https://api.com/v1", retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 3))
}
```
Ver `docs/integration-guide.md` sección [Configuración Multi-Entorno](#) para un ejemplo completo.

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

**¿Puedo configurar retry solo para ciertos endpoints?**
No directamente — `RetryPolicy` se aplica a nivel de `NetworkConfig` (todas las requests del executor). Si necesitas comportamiento diferente, crea dos executors: uno con retry para operaciones idempotentes (GET) y otro sin retry para operaciones que mutan estado (POST).

---

### Vulnerabilidades y hardening

**¿Qué pasa si alguien descompila la app y ve las API keys?**
Las API keys hardcodeadas en código son siempre vulnerables a ingeniería inversa, con o sin el SDK. El SDK mitiga esto de varias formas:
1. **`AndroidSecretStore`** almacena credenciales cifradas — no en código.
2. **`Credential.toString()` redacta valores** — no aparecen en dumps de memoria.
3. Para keys que deben estar en la app, usa `BuildConfig` + ofuscación.

La mejor práctica es que las API keys sensibles se obtengan vía un endpoint autenticado, no embebidas en el binario.

**¿Necesito ProGuard/R8 rules para el SDK?**
El SDK está construido como módulo KMP y usa `kotlinx.serialization` (que ya maneja ofuscación correctamente vía sus plugins). No necesitas reglas ProGuard adicionales para el SDK. Sin embargo, asegúrate de que el plugin de serialización esté aplicado — si no, R8 puede strip las anotaciones `@Serializable` y romper la deserialización.

**¿El SDK sanitiza datos sensibles en los logs de métricas y trazas?**
Sí. Además del `LoggingObserver`:
- `MetricsObserver` y `TracingObserver` usan `sanitizePath()` para strip query parameters de los paths antes de emitir tags (evita filtrar IDs, tokens en query strings).
- `NetworkLogger.NOOP` es el default — el SDK no imprime nada a menos que tú configures un logger.

**¿Cómo evito que información interna de `diagnostic` se muestre al usuario?**
Nunca uses `error.diagnostic?.description` en la UI. Usa siempre `error.message`:
```kotlin
// ✅ SEGURO — message es user-facing
showSnackbar(error.message)  // "Unable to reach the server"

// ❌ INSEGURO — diagnostic contiene stack traces e info interna
showSnackbar(error.diagnostic?.description ?: "Error")  // "java.net.UnknownHostException: ..."
```
`diagnostic` es solo para logging interno y debugging. `message` siempre es seguro y legible.

**¿El SDK protege contra replay attacks?**
El SDK no implementa protección anti-replay directamente (eso es responsabilidad del backend). Sin embargo, puedes agregar un nonce o timestamp vía un `RequestInterceptor`:
```kotlin
val antiReplayInterceptor = RequestInterceptor { request, _ ->
    request.copy(headers = request.headers + mapOf(
        "X-Request-Nonce" to UUID.randomUUID().toString(),
        "X-Request-Timestamp" to System.currentTimeMillis().toString()
    ))
}
```

**¿El SDK es vulnerable a SSL stripping?**
No, si usas la configuración por defecto. `NetworkConfig` rechaza URLs `http://` por defecto. Además, si configuras `DefaultTrustPolicy` con certificate pinning, un atacante no podría interceptar la conexión ni con un certificado CA falso.

---

### Documentación

**¿Dónde está la documentación completa?**
- `docs/integration-guide.md` — Guía completa de integración paso a paso
- `docs/security-checklist.md` — Checklist OWASP MASVS con todas las protecciones
- `network-core/README.md` — Contratos y pipeline de ejecución
- `security-core/README.md` — Credenciales, sesiones, almacenamiento seguro
- `docs/diagrams/` — Diagramas de arquitectura (SVG)
