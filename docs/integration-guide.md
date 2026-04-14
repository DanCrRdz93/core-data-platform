# Guía de Integración

**Cómo integrar el SDK Core Data Platform en tu aplicación**

Esta guía te lleva paso a paso por todo lo necesario para consumir el SDK en una aplicación Kotlin Multiplatform nueva o existente — desde agregar dependencias hasta hacer tu primera request y manejar errores correctamente.

---

## Tabla de Contenidos

- [Prerequisitos](#prerequisitos)
- [Agregar Dependencias](#agregar-dependencias)
- [Configuración Inicial](#configuración-inicial)
- [Construir el Pipeline de Ejecución](#construir-el-pipeline-de-ejecución)
- [Crear un Módulo de Dominio](#crear-un-módulo-de-dominio)
- [Consumir Resultados](#consumir-resultados)
- [Agregar Autenticación](#agregar-autenticación)
- [Agregar Observabilidad](#agregar-observabilidad)
- [Configuración Multi-Entorno](#configuración-multi-entorno)
- [Manejo de Errores en Detalle](#manejo-de-errores-en-detalle)
- [Recomendaciones de Arquitectura](#recomendaciones-de-arquitectura)
- [Errores Comunes y Soluciones](#errores-comunes-y-soluciones)
- [Qué NO Hacer](#qué-no-hacer)

---

## Prerequisitos

### Herramientas

| Requisito | Versión Mínima | Notas |
|---|---|---|
| Kotlin | 2.1.20 | Plugin Kotlin Multiplatform habilitado |
| Gradle | 9.3.1 | Version catalog (`libs.versions.toml`) recomendado |
| AGP | 9.1.0 | Usa el plugin `com.android.kotlin.multiplatform.library` |
| Android Studio | Ladybug+ | Soporte KMP requerido |
| Xcode | 15+ | Para compilación iOS |
| Android `compileSdk` | 36 | |
| Android `minSdk` | 29 | Android 10+ |

### Conocimientos Previos

Deberías estar familiarizado con:

- Coroutines de Kotlin (`suspend`, `Flow`, `StateFlow`)
- Modelo de source sets de Kotlin Multiplatform (`commonMain`, `androidMain`, `iosMain`)
- Sealed classes y expresiones `when` de Kotlin
- Proyectos multi-módulo con Gradle

---

## Agregar Dependencias

### Paso 1: Registrar los módulos del SDK

Si el SDK vive como un composite build local o módulos incluidos, agrégalos a tu `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
include(":network-core")
include(":network-ktor")
include(":security-core")
```

Si el SDK está publicado en un repositorio Maven, usa coordenadas (reemplaza con tu grupo/versión real):

```kotlin
// build.gradle.kts of your domain module
dependencies {
    implementation("com.dancr.platform:network-core:1.0.0")
    implementation("com.dancr.platform:network-ktor:1.0.0")
    implementation("com.dancr.platform:security-core:1.0.0")
}
```

### Paso 2: Agregar dependencias a tu módulo de dominio

```kotlin
// your-domain-module/build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)  // for JSON DTOs
}

kotlin {
    android {
        namespace = "com.yourcompany.yourmodule"
        compileSdk = 36
        minSdk = 29
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // Módulos del SDK
            implementation(project(":network-core"))
            implementation(project(":network-ktor"))
            implementation(project(":security-core"))

            // Requerido para coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Requerido para serialización JSON en tus DTOs
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

### Qué te da cada dependencia

| Dependencia | Obtienes |
|---|---|
| `:network-core` | `HttpRequest`, `SafeRequestExecutor`, `NetworkResult`, `NetworkError`, `RemoteDataSource`, `NetworkConfig`, `RetryPolicy`, `RequestInterceptor`, `ResponseInterceptor`, `NetworkEventObserver` |
| `:network-ktor` | `KtorHttpEngine`, `KtorErrorClassifier` — el transporte concreto |
| `:security-core` | `Credential`, `CredentialProvider`, `CredentialHeaderMapper`, `SessionController`, `SecretStore`, `TrustPolicy`, `LogSanitizer` |

> **Tip:** Si tu módulo no necesita autenticación ni funcionalidades de seguridad, puedes omitir `:security-core`. El stack de networking funciona de forma independiente.

---

## Configuración Inicial

### NetworkConfig

Toda aplicación comienza definiendo un `NetworkConfig` — la configuración base compartida por todas las requests:

```kotlin
import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.config.RetryPolicy
import kotlin.time.Duration.Companion.seconds

val networkConfig = NetworkConfig(
    baseUrl = "https://api.yourcompany.com/v1",
    defaultHeaders = mapOf(
        "Accept" to "application/json",
        "X-App-Platform" to "android",   // or "ios"
        "X-App-Version" to BuildConfig.VERSION_NAME
    ),
    connectTimeout = 15.seconds,
    readTimeout = 30.seconds,
    writeTimeout = 30.seconds,
    retryPolicy = RetryPolicy.ExponentialBackoff(
        maxRetries = 3,
        initialDelay = 1.seconds,
        maxDelay = 15.seconds,
        multiplier = 2.0
    )
)
```

### Parámetros de configuración

| Parámetro | Tipo | Valor por defecto | Descripción |
|---|---|---|---|
| `baseUrl` | `String` | *(requerido)* | URL base antepuesta a todas las rutas de request. No puede estar vacía. |
| `defaultHeaders` | `Map<String, String>` | `emptyMap()` | Headers agregados a cada request. Los headers por request sobreescriben estos. |
| `connectTimeout` | `Duration` | `30.seconds` | Timeout de conexión TCP |
| `readTimeout` | `Duration` | `30.seconds` | Timeout completo de request (Ktor `requestTimeoutMillis`) |
| `writeTimeout` | `Duration` | `30.seconds` | Timeout de socket (Ktor `socketTimeoutMillis`) |
| `retryPolicy` | `RetryPolicy` | `RetryPolicy.None` | Estrategia de reintento automático para errores reintentables |

### Opciones de política de reintento

```kotlin
// Sin reintentos (por defecto)
RetryPolicy.None

// Delay fijo entre reintentos
RetryPolicy.FixedDelay(
    maxRetries = 3,
    delay = 2.seconds
)

// Backoff exponencial
RetryPolicy.ExponentialBackoff(
    maxRetries = 3,
    initialDelay = 1.seconds,
    maxDelay = 30.seconds,
    multiplier = 2.0
)
```

---

## Construir el Pipeline de Ejecución

El pipeline de ejecución es donde todas las piezas se unen. Constrúyelo una vez por configuración de API y compártelo entre data sources.

### Configuración mínima (sin auth, sin observabilidad)

```kotlin
import com.dancr.platform.network.execution.DefaultSafeRequestExecutor
import com.dancr.platform.network.ktor.KtorHttpEngine
import com.dancr.platform.network.ktor.KtorErrorClassifier

val engine = KtorHttpEngine.create(networkConfig)

val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = networkConfig,
    classifier = KtorErrorClassifier()
)
```

### Configuración completa (auth + observabilidad)

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = KtorHttpEngine.create(networkConfig),
    config = networkConfig,
    classifier = KtorErrorClassifier(),
    interceptors = listOf(
        authInterceptor,           // inyecta header Authorization
        tracingInterceptor         // inyecta header X-Trace-Id
    ),
    responseInterceptors = listOf(
        loggingResponseInterceptor // registra el status de la respuesta
    ),
    observers = listOf(
        metricsObserver,           // registra latencia, tasa de errores
        analyticsObserver          // rastrea patrones de requests
    )
)
```

### Gestión del ciclo de vida

El `KtorHttpEngine` envuelve un `HttpClient` de Ktor. Debes:

1. **Crear un engine por configuración** — no uno por request.
2. **Llamar `engine.close()`** cuando la aplicación se cierra o el scope se cancela.
3. Si usas un framework de DI (Koin, Hilt), registra el engine como singleton con scope del ciclo de vida de la aplicación.

```kotlin
// En tu módulo de DI
single { KtorHttpEngine.create(networkConfig) }
single<SafeRequestExecutor> {
    DefaultSafeRequestExecutor(
        engine = get(),
        config = networkConfig,
        classifier = KtorErrorClassifier()
    )
}

// Al cerrar la app
onClose { get<HttpEngine>().close() }
```

---

## Crear un Módulo de Dominio

Sigue esta estructura por capas para cada dominio de API que integres:

```
tu-modulo/src/commonMain/kotlin/com/tuempresa/tumodulo/
├── dto/                  # Modelos técnicos que coinciden con el JSON
├── model/                # Modelos de dominio limpios
├── mapper/               # Conversión DTO → Dominio
├── datasource/           # Construye requests, delega al executor
├── repository/           # Mapea resultados de DTO a resultados de dominio
└── di/                   # Cableado del factory
```

### Paso 1: Definir DTOs

```kotlin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderDto(
    val id: Long,
    val status: String,
    val total: Double,
    @SerialName("created_at") val createdAt: String,
    val items: List<OrderItemDto> = emptyList()
)

@Serializable
data class OrderItemDto(
    val sku: String,
    val quantity: Int,
    val price: Double
)
```

**Reglas:**
- Siempre `@Serializable`.
- Usa `@SerialName` cuando la clave JSON difiere del nombre de la propiedad Kotlin.
- Valores por defecto para campos opcionales (`= emptyList()`, `= null`).
- Sin lógica de negocio aquí — son transportadores de datos puros.

### Paso 2: Definir modelos de dominio

```kotlin
data class Order(
    val id: Long,
    val status: OrderStatus,
    val totalAmount: String,     // formatted for display
    val createdAt: String,
    val itemCount: Int
)

enum class OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }
```

**Reglas:**
- Sin `@Serializable`.
- Usa el vocabulario de tu app, no el del API.
- Aplana estructuras anidadas si los consumidores no las necesitan.

### Paso 3: Crear un mapper

```kotlin
object OrderMapper {

    fun toDomain(dto: OrderDto): Order = Order(
        id = dto.id,
        status = parseStatus(dto.status),
        totalAmount = "$${dto.total}",
        createdAt = dto.createdAt,
        itemCount = dto.items.size
    )

    fun toDomain(dtos: List<OrderDto>): List<Order> = dtos.map(::toDomain)

    private fun parseStatus(raw: String): OrderStatus = try {
        OrderStatus.valueOf(raw.uppercase())
    } catch (_: IllegalArgumentException) {
        OrderStatus.PENDING
    }
}
```

### Paso 4: Construir el data source

```kotlin
import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.datasource.RemoteDataSource
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.result.NetworkResult
import kotlinx.serialization.json.Json

class OrderRemoteDataSource(
    executor: SafeRequestExecutor
) : RemoteDataSource(executor) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchOrders(): NetworkResult<List<OrderDto>> = execute(
        request = HttpRequest(
            path = "/orders",
            method = HttpMethod.GET
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )

    suspend fun fetchOrder(id: Long): NetworkResult<OrderDto> = execute(
        request = HttpRequest(
            path = "/orders/$id",
            method = HttpMethod.GET
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )

    suspend fun createOrder(body: ByteArray): NetworkResult<OrderDto> = execute(
        request = HttpRequest(
            path = "/orders",
            method = HttpMethod.POST,
            headers = mapOf("Content-Type" to "application/json"),
            body = body
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )
}
```

**Puntos clave:**
- Extiende `RemoteDataSource` — esto te da `protected fun execute()`.
- Inyecta `SafeRequestExecutor` — la interfaz, no la clase concreta.
- Crea `Json` una vez, reúsalo en todos los métodos.
- `ignoreUnknownKeys = true` protege contra evolución del API.
- Las rutas son relativas — el executor antepone `baseUrl`.

### Paso 5: Crear el repository

```kotlin
import com.dancr.platform.network.result.NetworkResult

class OrderRepository(
    private val dataSource: OrderRemoteDataSource
) {

    suspend fun getOrders(): NetworkResult<List<Order>> =
        dataSource.fetchOrders().map(OrderMapper::toDomain)

    suspend fun getOrder(id: Long): NetworkResult<Order> =
        dataSource.fetchOrder(id).map(OrderMapper::toDomain)
}
```

El único trabajo del repository es mapear `NetworkResult<Dto>` → `NetworkResult<Model>` usando `NetworkResult.map()`.

### Paso 6: Cablear todo junto

```kotlin
import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.execution.DefaultSafeRequestExecutor
import com.dancr.platform.network.ktor.KtorErrorClassifier
import com.dancr.platform.network.ktor.KtorHttpEngine

object OrderApiFactory {

    fun create(
        config: NetworkConfig,
        credentialProvider: CredentialProvider? = null
    ): OrderRepository {
        val engine = KtorHttpEngine.create(config)

        val interceptors = buildList {
            if (credentialProvider != null) {
                add(authInterceptor(credentialProvider))
            }
        }

        val executor = DefaultSafeRequestExecutor(
            engine = engine,
            config = config,
            classifier = KtorErrorClassifier(),
            interceptors = interceptors
        )

        return OrderRepository(OrderRemoteDataSource(executor))
    }

    private fun authInterceptor(provider: CredentialProvider) =
        RequestInterceptor { request, _ ->
            val credential = provider.current() ?: return@RequestInterceptor request
            val headers = CredentialHeaderMapper.toHeaders(credential)
            request.copy(headers = request.headers + headers)
        }
}
```

---

## Consumir Resultados

### En un ViewModel (Android)

```kotlin
class OrderViewModel(private val repository: OrderRepository) : ViewModel() {

    private val _state = MutableStateFlow<OrderScreenState>(OrderScreenState.Loading)
    val state: StateFlow<OrderScreenState> = _state.asStateFlow()

    fun loadOrders() {
        viewModelScope.launch {
            _state.value = OrderScreenState.Loading

            repository.getOrders().fold(
                onSuccess = { orders ->
                    _state.value = OrderScreenState.Content(orders)
                },
                onFailure = { error ->
                    _state.value = OrderScreenState.Error(error.message)
                }
            )
        }
    }
}

sealed interface OrderScreenState {
    data object Loading : OrderScreenState
    data class Content(val orders: List<Order>) : OrderScreenState
    data class Error(val message: String) : OrderScreenState
}
```

### Encadenar operaciones

```kotlin
// Secuencial: obtener orden, luego obtener pago
val result: NetworkResult<PaymentInfo> = repository.getOrder(orderId)
    .flatMap { order -> paymentRepository.getPayment(order.paymentId) }

// Transformar datos exitosos
val formatted: NetworkResult<String> = repository.getOrder(orderId)
    .map { order -> "${order.status}: ${order.totalAmount}" }
```

### Efectos secundarios

```kotlin
repository.getOrders()
    .onSuccess { orders -> analytics.track("orders_loaded", orders.size) }
    .onFailure { error -> logger.warn("Failed to load orders: ${error.diagnostic?.description}") }
    .fold(
        onSuccess = { /* update UI */ },
        onFailure = { /* show error */ }
    )
```

### Extraer valores directamente

```kotlin
// Extracción nullable (usar con moderación)
val orders: List<Order>? = repository.getOrders().getOrNull()
val error: NetworkError? = repository.getOrders().errorOrNull()

// Verificaciones booleanas
if (result.isSuccess) { /* ... */ }
if (result.isFailure) { /* ... */ }
```

> **Recomendación:** Prefiere `.fold()` para manejo exhaustivo. Usa `.getOrNull()` solo en tests o cuando genuinamente no te importa el caso de fallo.

---

## Agregar Autenticación

### Paso 1: Implementar CredentialProvider

```kotlin
import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.credential.CredentialProvider

class TokenCredentialProvider(
    private val tokenStore: TokenStore  // tu gestión de tokens
) : CredentialProvider {

    override suspend fun current(): Credential? {
        val token = tokenStore.getAccessToken() ?: return null
        return Credential.Bearer(token)
    }
}
```

### Paso 2: Construir el interceptor de auth

```kotlin
import com.dancr.platform.network.execution.RequestInterceptor
import com.dancr.platform.security.credential.CredentialHeaderMapper

fun authInterceptor(provider: CredentialProvider) =
    RequestInterceptor { request, _ ->
        val credential = provider.current()
            ?: return@RequestInterceptor request
        val headers = CredentialHeaderMapper.toHeaders(credential)
        request.copy(headers = request.headers + headers)
    }
```

`CredentialHeaderMapper` maneja todos los tipos de credencial automáticamente:

| Credencial | Header Generado |
|---|---|
| `Credential.Bearer("abc")` | `Authorization: Bearer abc` |
| `Credential.ApiKey("key", "X-API-Key")` | `X-API-Key: key` |
| `Credential.Basic("user", "pass")` | `Authorization: Basic dXNlcjpwYXNz` |
| `Credential.Custom("OAuth", props)` | Todas las entradas de `props` como headers |

### Paso 3: Cablear al executor

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    interceptors = listOf(authInterceptor(myCredentialProvider))
)
```

---

## Agregar Observabilidad

### Implementar NetworkEventObserver

Solo sobreescribe los callbacks que necesites — todos los métodos tienen implementaciones no-op por defecto:

```kotlin
import com.dancr.platform.network.observability.NetworkEventObserver

class AppMetricsObserver(
    private val metrics: MetricsClient
) : NetworkEventObserver {

    override fun onResponseReceived(
        request: HttpRequest,
        response: RawResponse,
        durationMs: Long,
        context: RequestContext?
    ) {
        metrics.recordLatency("http.request.duration", durationMs,
            tags = mapOf(
                "path" to request.path,
                "status" to response.statusCode.toString()
            )
        )
    }

    override fun onRequestFailed(
        request: HttpRequest,
        error: NetworkError,
        durationMs: Long,
        context: RequestContext?
    ) {
        metrics.increment("http.request.error",
            tags = mapOf(
                "path" to request.path,
                "type" to error::class.simpleName.orEmpty()
            )
        )
    }

    override fun onRetryScheduled(
        request: HttpRequest,
        attempt: Int,
        maxAttempts: Int,
        error: NetworkError,
        delayMs: Long
    ) {
        metrics.increment("http.request.retry",
            tags = mapOf("attempt" to attempt.toString())
        )
    }
}
```

### Cablear observers

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    observers = listOf(
        AppMetricsObserver(myMetrics),
        AppLoggingObserver(myLogger)
    )
)
```

---

## Configuración Multi-Entorno

Define configuraciones por entorno y selecciona en tiempo de compilación o inicio:

```kotlin
object ApiConfigs {

    val development = NetworkConfig(
        baseUrl = "https://dev-api.yourcompany.com/v1",
        defaultHeaders = mapOf("Accept" to "application/json"),
        connectTimeout = 30.seconds,
        readTimeout = 60.seconds,
        retryPolicy = RetryPolicy.None  // fallo rápido en dev
    )

    val staging = NetworkConfig(
        baseUrl = "https://staging-api.yourcompany.com/v1",
        defaultHeaders = mapOf("Accept" to "application/json"),
        connectTimeout = 15.seconds,
        readTimeout = 30.seconds,
        retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 2)
    )

    val production = NetworkConfig(
        baseUrl = "https://api.yourcompany.com/v1",
        defaultHeaders = mapOf("Accept" to "application/json"),
        connectTimeout = 10.seconds,
        readTimeout = 20.seconds,
        retryPolicy = RetryPolicy.ExponentialBackoff(
            maxRetries = 3,
            initialDelay = 1.seconds,
            maxDelay = 15.seconds
        )
    )
}

// Al inicializar
val config = when (BuildConfig.FLAVOR) {
    "dev" -> ApiConfigs.development
    "staging" -> ApiConfigs.staging
    else -> ApiConfigs.production
}
val repository = OrderApiFactory.create(config, credentialProvider)
```

---

## Manejo de Errores en Detalle

### Tipos de error y respuesta recomendada en UI

| Error | Cuándo ocurre | UI Recomendada |
|---|---|---|
| `Connectivity` | Sin internet, fallo DNS, servidor inalcanzable | "Revisa tu conexión" + botón reintentar |
| `Timeout` | Servidor muy lento | "El servidor está lento, intenta de nuevo" + botón reintentar |
| `Cancelled` | El usuario navegó fuera, scope cancelado | Sin UI — la operación fue cancelada intencionalmente |
| `Authentication` | 401 — token expirado o inválido | Redirigir a login, o disparar refresh silencioso |
| `Authorization` | 403 — el usuario no tiene permisos | "No tienes acceso a esta funcionalidad" |
| `ClientError` | 4xx (otros) — request inválida | "Algo salió mal" + loguear `diagnostic` |
| `ServerError` | 5xx — fallo del servidor | "Estamos con problemas" (reintentado automáticamente) |
| `Serialization` | El body de respuesta no coincide con el DTO | "Algo salió mal" + loguear `diagnostic` urgentemente (desajuste de contrato API) |
| `ResponseValidation` | Un validador custom rechazó una respuesta 2xx | Depende de la lógica de tu validador |
| `Unknown` | Excepción no clasificada | "Algo salió mal" + loguear `diagnostic` |

### Patrón: manejo exhaustivo de errores

```kotlin
result.onFailure { error ->
    when (error) {
        is NetworkError.Connectivity -> showRetryBanner("No connection")
        is NetworkError.Timeout -> showRetryBanner("Request timed out")
        is NetworkError.Cancelled -> { /* ignorar — acción del usuario */ }

        is NetworkError.Authentication -> navigateToLogin()
        is NetworkError.Authorization -> showAccessDenied()
        is NetworkError.ClientError -> showGenericError(error.message)
        is NetworkError.ServerError -> showGenericError("Server issue, we're on it")

        is NetworkError.Serialization -> {
            showGenericError(error.message)
            crashlytics.logNonFatal("API contract mismatch", error.diagnostic?.cause)
        }
        is NetworkError.ResponseValidation -> showGenericError(error.message)
        is NetworkError.Unknown -> showGenericError(error.message)
    }

    // Siempre loguear diagnostic internamente (nunca mostrar al usuario)
    error.diagnostic?.let { d ->
        logger.error("${error::class.simpleName}: ${d.description}", d.cause)
    }
}
```

### Patrón: manejo simplificado de errores

Para pantallas que no necesitan manejo por tipo:

```kotlin
result.fold(
    onSuccess = { data -> showContent(data) },
    onFailure = { error -> showError(error.message) }
)
```

`error.message` siempre es seguro para el usuario y legible.

---

## Recomendaciones de Arquitectura

### Hacer

- **Un `NetworkConfig` por URL base de API.** Si tu app habla con `api.example.com` y `auth.example.com`, crea dos configs y dos executors.
- **Un factory por dominio.** `OrderApiFactory`, `PaymentApiFactory`, `UserApiFactory`. Cada uno cablea su propio data source y repository.
- **Inyectar `SafeRequestExecutor`, nunca `DefaultSafeRequestExecutor`.** Programa contra la interfaz.
- **Usar `RequestContext` para trazabilidad.** Pasa un `operationId` para poder correlacionar logs, métricas y trazas con operaciones específicas.
- **Manejar `NetworkError.Serialization` como señal crítica.** Significa que la respuesta del API no coincide con tus DTOs. Es un problema de despliegue, no un error transitorio.
- **Mantener los mappers puros.** Sin I/O, sin estado, sin efectos secundarios. Solo entrada → salida.
- **Crear `Json` una vez por data source.** No por request.

```kotlin
// ✅ Correcto
class MyDataSource(executor: SafeRequestExecutor) : RemoteDataSource(executor) {
    private val json = Json { ignoreUnknownKeys = true }
    // ... use json in every method
}

// ❌ Incorrecto
suspend fun fetchData(): NetworkResult<Data> = execute(
    request = HttpRequest(...),
    deserialize = { response ->
        val json = Json { ignoreUnknownKeys = true } // ¡desperdicio!
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

### Estrategia de testing

```kotlin
// Mockear el executor para tests de data source
class FakeExecutor(private val result: NetworkResult<*>) : SafeRequestExecutor {
    override suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext?,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> = result as NetworkResult<T>
}

// Testear el data source
@Test
fun `fetchOrders returns mapped orders on success`() = runTest {
    val fakeResponse = RawResponse(200, emptyMap(), ordersJson.encodeToByteArray())
    val executor = FakeExecutor(NetworkResult.Success(fakeResponse))
    val dataSource = OrderRemoteDataSource(executor)

    val result = dataSource.fetchOrders()
    assertTrue(result.isSuccess)
}
```

---

## Errores Comunes y Soluciones

### 1. `Unresolved reference: KtorHttpEngine`

**Causa:** Falta la dependencia `:network-ktor`.

**Solución:** Agrega `implementation(project(":network-ktor"))` a los `commonMain.dependencies` de tu módulo.

### 2. `Unresolved reference: Json` o `@Serializable`

**Causa:** Falta el plugin o la dependencia de serialización.

**Solución:**
```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.serialization)
}
// commonMain.dependencies
implementation(libs.kotlinx.serialization.json)
```

### 3. `kotlinx.serialization.json.internal.JsonDecodingException: Unexpected JSON token`

**Causa:** El body de la respuesta no coincide con la estructura de tu DTO.

**Solución:** Verifica que tu data class `@Serializable` coincida con la respuesta real del API. Usa `ignoreUnknownKeys = true` en tu instancia de `Json` para tolerar campos nuevos.

### 4. `NetworkError.Serialization` en cada request

**Causa:** Frecuentemente un body nulo (fallo del force-unwrap `response.body!!`). El API podría retornar 204 No Content o un body vacío.

**Solución:** Protege contra body nulo en tu lambda de deserialización:

```kotlin
deserialize = { response ->
    val bodyString = response.body?.decodeToString()
        ?: throw IllegalStateException("Empty response body")
    json.decodeFromString(bodyString)
}
```

### 5. `NetworkError.Connectivity` cuando el dispositivo tiene internet

**Causa:** `baseUrl` incorrecto en `NetworkConfig`, o el servidor está caído.

**Solución:** Revisa `error.diagnostic?.description` para ver el mensaje de la excepción subyacente. Verifica que la URL base sea correcta y alcanzable.

### 6. Error de header `Content-Type` duplicado de Ktor

**Causa:** Se establece `Content-Type` tanto en `HttpRequest.headers` como vía el body.

**Solución:** `KtorHttpEngine` ya extrae `Content-Type` de tus headers y lo pasa al body. Solo establécelo en tu `HttpRequest.headers`:

```kotlin
HttpRequest(
    path = "/orders",
    method = HttpMethod.POST,
    headers = mapOf("Content-Type" to "application/json"),
    body = jsonString.encodeToByteArray()
)
```

### 7. Headers de auth no aparecen en las requests

**Causa:** `CredentialProvider.current()` retorna `null`, o el interceptor de auth no está en la lista de interceptors.

**Solución:** Verifica que tu `CredentialProvider` retorne un `Credential` no nulo, y que el interceptor se pase al `DefaultSafeRequestExecutor`.

### 8. Los reintentos no están ocurriendo

**Causa:** `RetryPolicy.None` es el valor por defecto. O el tipo de error no es reintentable.

**Solución:** Establece una política de reintento en `NetworkConfig`, y verifica que el tipo de error tenga `isRetryable = true`. Solo `Connectivity`, `Timeout` y `ServerError` son reintentables por defecto.

---

## Qué NO Hacer

### ❌ No importes tipos de Ktor en módulos de dominio

```kotlin
// INCORRECTO — filtra detalles de transporte
import io.ktor.client.statement.HttpResponse

class MyDataSource {
    suspend fun fetch(): HttpResponse { ... }
}
```

Usa `HttpRequest`, `RawResponse` y `SafeRequestExecutor` exclusivamente.

### ❌ No crees un HttpEngine por request

```kotlin
// INCORRECTO — crea un nuevo cliente HTTP en cada llamada
suspend fun fetchData() {
    val engine = KtorHttpEngine.create(config)  // ¡costoso!
    executor.execute(...)
    engine.close()
}
```

Crea un engine al inicio, compártelo.

### ❌ No captures NetworkResult.Failure como excepción

```kotlin
// INCORRECTO — NetworkResult es un valor, no una excepción
try {
    val result = repository.getOrders()
} catch (e: NetworkResult.Failure) {  // Esto no compila
    ...
}
```

Usa `.fold()`, `.onFailure()`, o `when (result)`.

### ❌ No muestres `diagnostic` al usuario

```kotlin
// INCORRECTO — diagnostic contiene stack traces y detalles internos
showToast(error.diagnostic?.description ?: "Error")

// CORRECTO — message siempre es seguro para el usuario
showToast(error.message)
```

### ❌ No pongas lógica de negocio en interceptors

```kotlin
// INCORRECTO — los interceptors son para infraestructura
val priceValidator = RequestInterceptor { request, _ ->
    if (calculateTotal(request) > MAX_ORDER) throw TooExpensiveException()
    request
}

// CORRECTO — pon lógica de negocio en el repository o use case
class OrderRepository {
    suspend fun placeOrder(order: Order): NetworkResult<Confirmation> {
        require(order.total <= MAX_ORDER) { "Order exceeds limit" }
        return dataSource.createOrder(order.toDto())
    }
}
```

### ❌ No dependas de `network-core` desde `security-core` (ni viceversa)

Estos módulos son intencionalmente independientes. Si necesitas conectarlos (ej. invalidar sesión en un 401), hazlo en la capa consumidora:

```kotlin
// En tu módulo de app — no en el SDK
repository.getOrders().onFailure { error ->
    if (error is NetworkError.Authentication) {
        sessionController.endSession()
    }
}
```

### ❌ No hardcodees URLs base en data sources

```kotlin
// INCORRECTO
class MyDataSource {
    private val baseUrl = "https://api.prod.example.com"
}

// CORRECTO — la URL base viene de NetworkConfig
class MyDataSource(executor: SafeRequestExecutor) : RemoteDataSource(executor) {
    // la ruta es relativa; el executor antepone baseUrl
    suspend fun fetch() = execute(
        request = HttpRequest(path = "/data"),
        deserialize = { ... }
    )
}
```

### ❌ No compartas un solo executor entre diferentes URLs base

```kotlin
// INCORRECTO — ambas APIs usan la misma baseUrl de una sola config
val executor = DefaultSafeRequestExecutor(engine, configForApiA, ...)
val repoA = OrderRepository(OrderDataSource(executor))    // baseUrl correcto
val repoB = PaymentRepository(PaymentDataSource(executor)) // ¡baseUrl incorrecto!
```

Crea configs y executors separados para diferentes APIs.

---

*Para más detalles, consulta los READMEs de cada módulo en `network-core/`, `network-ktor/`, `security-core/` y `sample-api/`.*
