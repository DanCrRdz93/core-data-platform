# Guía de Integración

**Cómo integrar el SDK Core Data Platform en tu aplicación**

Esta guía te lleva paso a paso por todo lo necesario para consumir el SDK en una aplicación Kotlin Multiplatform nueva o existente — desde agregar dependencias hasta hacer tu primera request y manejar errores correctamente.

---

## Tabla de Contenidos

- [Prerequisitos](#prerequisitos)
- [Agregar Dependencias](#agregar-dependencias)
- [Configuración Inicial](#configuración-inicial)
- [Construir el Pipeline de Ejecución](#construir-el-pipeline-de-ejecución)
- [Construir la Capa de Data](#construir-la-capa-de-data)
- [Consumir Resultados](#consumir-resultados)
- [RequestContext — Trazabilidad y Control por Request](#requestcontext--trazabilidad-y-control-por-request)
- [ResponseInterceptor — Post-procesamiento de Respuestas](#responseinterceptor--post-procesamiento-de-respuestas)
- [Agregar Autenticación](#agregar-autenticación)
- [Certificate Pinning](#certificate-pinning)
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

### Paso 1: Agregar dependencias del SDK

El SDK está publicado en **Maven Central**. Agrega las dependencias en tu `build.gradle.kts`:

```kotlin
// build.gradle.kts de tu módulo de dominio
dependencies {
    implementation("io.github.dancrrdz93:network-core:1.1.0")
    implementation("io.github.dancrrdz93:network-ktor:1.1.0")
    implementation("io.github.dancrrdz93:security-core:1.1.0")
}
```

Asegúrate de que `mavenCentral()` esté en tus repositorios (viene por defecto en la mayoría de proyectos):

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

Si el SDK vive como módulos locales de tu proyecto, usa `implementation(project(":network-core"))` en su lugar.

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
            // Módulos del SDK (Maven Central)
            implementation("io.github.dancrrdz93:network-core:1.1.0")
            implementation("io.github.dancrrdz93:network-ktor:1.1.0")
            implementation("io.github.dancrrdz93:security-core:1.1.0")

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
| `allowInsecureConnections` | `Boolean` | `false` | Si `true`, permite `http://` como `baseUrl`. **Solo para desarrollo local** (localhost, emulador). En producción SIEMPRE debe ser `false` (OWASP MASVS-NETWORK-1) |

### Opciones de política de reintento

Solo los errores con `isRetryable = true` se reintentan automáticamente. Por defecto, son reintentables: `Connectivity`, `Timeout` y `ServerError`.

| Política | Comportamiento | Cuándo usarla |
|---|---|---|
| `RetryPolicy.None` | Sin reintentos (1 solo intento) | Desarrollo, operaciones no idempotentes |
| `RetryPolicy.FixedDelay(maxRetries, delay)` | Delay constante entre reintentos | APIs con rate limiting predecible |
| `RetryPolicy.ExponentialBackoff(maxRetries, initialDelay, maxDelay, multiplier)` | Delay crece exponencialmente | **Recomendada para producción** |

```kotlin
// Sin reintentos (por defecto)
RetryPolicy.None

// Delay fijo entre reintentos
RetryPolicy.FixedDelay(
    maxRetries = 3,       // 3 reintentos → 4 intentos totales
    delay = 2.seconds     // 2s entre cada reintento
)

// Backoff exponencial (recomendado)
RetryPolicy.ExponentialBackoff(
    maxRetries = 3,        // 3 reintentos → 4 intentos totales
    initialDelay = 1.seconds,  // primer reintento: 1s
    maxDelay = 30.seconds,     // delay máximo (cap)
    multiplier = 2.0           // cada reintento: delay × 2
    // Delays resultantes: 1s, 2s, 4s (capped a maxDelay)
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
    engine = KtorHttpEngine.create(networkConfig, trustPolicy),
    config = networkConfig,
    classifier = KtorErrorClassifier(),
    interceptors = listOf(
        authInterceptor,           // inyecta header Authorization
        tracingInterceptor         // inyecta header X-Trace-Id
    ),
    responseInterceptors = listOf(
        rateLimitInterceptor       // extrae headers de rate limit
    ),
    observers = listOf(
        loggingObserver,           // logging de requests
        metricsObserver,           // registra latencia, tasa de errores
        analyticsObserver          // rastrea patrones de requests
    )
)
```

### Referencia: parámetros de `DefaultSafeRequestExecutor`

| Parámetro | Tipo | Requerido | Descripción |
|---|---|---|---|
| `engine` | `HttpEngine` | ✅ | Transporte HTTP. Usa `KtorHttpEngine.create(config)` o `KtorHttpEngine.create(config, trustPolicy)` |
| `config` | `NetworkConfig` | ✅ | Configuración base (baseUrl, headers, timeouts, retry) |
| `validator` | `ResponseValidator` | ❌ | Valida respuestas antes de deserializar. Default: `DefaultResponseValidator()` (acepta 2xx) |
| `classifier` | `ErrorClassifier` | ❌ | Clasifica errores HTTP en `NetworkError`. Default: `DefaultErrorClassifier()`. Usa `KtorErrorClassifier()` con Ktor |
| `interceptors` | `List<RequestInterceptor>` | ❌ | Se ejecutan **antes** del transporte, en orden. Ej: auth, tracing, headers custom |
| `responseInterceptors` | `List<ResponseInterceptor>` | ❌ | Se ejecutan **después** del transporte pero **antes** de validación. Ej: rate limit, cache |
| `observers` | `List<NetworkEventObserver>` | ❌ | Observan el ciclo de vida sin modificar requests/responses. Ej: logging, métricas, tracing |

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

## Construir la Capa de Data

Por cada API que integres, crea un módulo (ej. `:orders-api`, `:payments-api`) con la siguiente estructura. La mayor parte del trabajo vive en la **capa data** — los modelos de dominio se definen brevemente solo como destino del mapeo.

```
tu-modulo/src/commonMain/kotlin/com/tuempresa/tumodulo/
│
│  ── Capa Domain (mínima: solo entidades y vocabulario de tu app) ──
├── model/                # Modelos de dominio limpios (sin @Serializable)
│
│  ── Capa Data (el grueso del módulo: aquí vive el SDK) ──
├── dto/                  # Modelos técnicos @Serializable (contrato del API)
├── mapper/               # Conversión DTO → Modelo de dominio
├── datasource/           # Construye requests HTTP, delega al executor
├── repository/           # Mapea NetworkResult<Dto> → NetworkResult<Model>
│
│  ── Ensamblaje ──
└── di/                   # Factory que cablea executor → datasource → repository
```

> **¿Por qué los DTOs no están en domain?** Los DTOs representan el contrato del API externo — no te pertenecen. Son parte de la capa data porque dependen de `@Serializable` y reflejan la estructura JSON del servidor, no el vocabulario de tu negocio. Los modelos de dominio (`model/`) sí te pertenecen y son independientes del API.

### Paso 1: Definir DTOs (capa data)

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

### Paso 2: Definir modelos de dominio (capa domain)

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

### Paso 3: Crear un mapper (capa data)

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

### Paso 4: Construir el data source (capa data)

El data source es la clase que construye requests HTTP y las ejecuta a través del SDK. Extiende `RemoteDataSource` para acceder a `execute()`, que es el puente entre tu código y el pipeline de ejecución (retry, interceptors, clasificación de errores).

**Estructura base** — todos los escenarios que siguen van dentro de esta clase:

```kotlin
import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.datasource.RemoteDataSource
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.result.NetworkResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class OrderRemoteDataSource(
    executor: SafeRequestExecutor
) : RemoteDataSource(executor) {

    private val json = Json { ignoreUnknownKeys = true }

    // Los métodos de los escenarios siguientes van aquí
}
```

- **`RemoteDataSource(executor)`** — clase base del SDK que expone `protected fun execute()`.
- **`SafeRequestExecutor`** — inyecta la interfaz, no la implementación concreta.
- **`Json { ignoreUnknownKeys = true }`** — se crea una vez y se reutiliza en todos los métodos. `ignoreUnknownKeys` protege contra cambios del API (si el backend agrega un campo nuevo, tu app no se rompe).

---

#### Escenario: Obtener una lista de recursos

Tu app necesita mostrar el listado de órdenes del usuario. El endpoint `GET /orders` retorna un array JSON.

```kotlin
suspend fun fetchOrders(): NetworkResult<List<OrderDto>> = execute(
    request = HttpRequest(
        path = "/orders",
        method = HttpMethod.GET
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

#### Escenario: Obtener un recurso por ID

El usuario selecciona una orden de la lista y tu app necesita cargar su detalle. El endpoint `GET /orders/{id}` retorna un objeto JSON.

```kotlin
suspend fun fetchOrder(id: Long): NetworkResult<OrderDto> = execute(
    request = HttpRequest(
        path = "/orders/$id",
        method = HttpMethod.GET
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

#### Escenario: Paginar resultados

La lista de órdenes puede tener miles de registros. El backend soporta paginación con `GET /orders?page=1&size=20`. Usa el campo `queryParams` de `HttpRequest` — nunca concatenes parámetros manualmente en el `path`.

```kotlin
suspend fun fetchOrders(page: Int, size: Int): NetworkResult<List<OrderDto>> = execute(
    request = HttpRequest(
        path = "/orders",
        method = HttpMethod.GET,
        queryParams = mapOf(
            "page" to page.toString(),
            "size" to size.toString()
        )
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

#### Escenario: Filtrar con parámetros opcionales

Tu app tiene filtros de búsqueda donde el usuario puede filtrar por estado (`?status=SHIPPED`) y/o por fecha (`?from=2024-01-01`), pero ambos son opcionales. Solo se envían los parámetros que el usuario seleccionó.

```kotlin
suspend fun fetchOrders(status: String? = null, from: String? = null): NetworkResult<List<OrderDto>> {
    val params = buildMap {
        status?.let { put("status", it) }
        from?.let { put("from", it) }
    }
    return execute(
        request = HttpRequest(
            path = "/orders",
            method = HttpMethod.GET,
            queryParams = params
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )
}
```

#### Escenario: Crear un recurso nuevo

El usuario llena un formulario para crear una nueva orden. Tu app envía los datos al backend vía `POST /orders` con un body JSON. El servidor responde con la orden creada (incluyendo su `id` generado).

```kotlin
suspend fun createOrder(dto: CreateOrderDto): NetworkResult<OrderDto> = execute(
    request = HttpRequest(
        path = "/orders",
        method = HttpMethod.POST,
        headers = mapOf("Content-Type" to "application/json"),
        body = json.encodeToString(dto).encodeToByteArray()
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

> Para todos los métodos con body (`POST`, `PUT`, `PATCH`), siempre incluye `"Content-Type" to "application/json"` en los headers.

#### Escenario: Reemplazar un recurso completo

El usuario edita todos los campos de una orden existente y guarda. Tu app envía el objeto completo vía `PUT /orders/{id}`, que reemplaza el recurso entero en el servidor.

```kotlin
suspend fun updateOrder(id: Long, dto: UpdateOrderDto): NetworkResult<OrderDto> = execute(
    request = HttpRequest(
        path = "/orders/$id",
        method = HttpMethod.PUT,
        headers = mapOf("Content-Type" to "application/json"),
        body = json.encodeToString(dto).encodeToByteArray()
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

#### Escenario: Actualizar parcialmente un recurso

El usuario solo cambia el estado de una orden (ej. de "PENDING" a "CONFIRMED"). En vez de enviar el objeto completo, envías solo los campos que cambiaron vía `PATCH /orders/{id}`.

```kotlin
suspend fun patchOrderStatus(id: Long, status: String): NetworkResult<OrderDto> = execute(
    request = HttpRequest(
        path = "/orders/$id",
        method = HttpMethod.PATCH,
        headers = mapOf("Content-Type" to "application/json"),
        body = json.encodeToString(mapOf("status" to status)).encodeToByteArray()
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

#### Escenario: Eliminar un recurso

El usuario elimina una orden. El endpoint `DELETE /orders/{id}` típicamente retorna `204 No Content` — es decir, una respuesta exitosa sin body. En ese caso, deserializamos a `Unit`.

```kotlin
suspend fun deleteOrder(id: Long): NetworkResult<Unit> = execute(
    request = HttpRequest(
        path = "/orders/$id",
        method = HttpMethod.DELETE
    ),
    deserialize = { /* 204 No Content — no hay body que deserializar */ }
)
```

#### Escenario: Enviar headers específicos por request

Tu API de órdenes tiene un endpoint `GET /orders/{id}` que, para socios comerciales (partners), requiere un header adicional `X-Partner-Id`. Este header solo aplica a esta request, no a todas. Los headers del `HttpRequest` se combinan con los `defaultHeaders` de `NetworkConfig`; si hay colisión, los del `HttpRequest` tienen prioridad.

```kotlin
suspend fun fetchOrderForPartner(id: Long, partnerId: String): NetworkResult<OrderDto> = execute(
    request = HttpRequest(
        path = "/orders/$id",
        method = HttpMethod.GET,
        headers = mapOf("X-Partner-Id" to partnerId)
    ),
    deserialize = { response ->
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

#### Escenario: Manejar respuestas con body vacío o nulo

Tu API tiene un endpoint `POST /orders/{id}/archive` que archiva una orden. Dependiendo del estado, el servidor puede responder con `200` y la orden archivada, o con `200` y body vacío si la orden ya estaba archivada. Debes proteger la deserialización contra `null`.

```kotlin
suspend fun archiveOrder(id: Long): NetworkResult<OrderDto?> = execute(
    request = HttpRequest(
        path = "/orders/$id/archive",
        method = HttpMethod.POST,
        headers = mapOf("Content-Type" to "application/json")
    ),
    deserialize = { response ->
        val bodyString = response.body?.decodeToString()
        if (bodyString.isNullOrBlank()) null
        else json.decodeFromString(bodyString)
    }
)
```

---

#### Referencia rápida: campos de `HttpRequest`

| Campo | Tipo | Descripción |
|---|---|---|
| `path` | `String` | Ruta relativa (el executor antepone `baseUrl`). Ej: `"/orders"`, `"/orders/42"` |
| `method` | `HttpMethod` | `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS` |
| `headers` | `Map<String, String>` | Headers adicionales. Se combinan con `NetworkConfig.defaultHeaders` (los del request tienen prioridad) |
| `queryParams` | `Map<String, String>` | Parámetros de query string. El SDK los serializa y agrega al URL automáticamente |
| `body` | `ByteArray?` | Body de la request. Típicamente `json.encodeToString(dto).encodeToByteArray()` |

**Puntos clave:**
- Las rutas son relativas — el executor antepone `baseUrl`.
- Usa `queryParams` en vez de concatenar parámetros manualmente en el path.

> **Convención de naming: `fetch` vs `get`**
> Los métodos del **DataSource** usan el prefijo **`fetch`** (ej. `fetchOrders()`, `fetchOrder(id)`) porque acceden directamente a la red y retornan **DTOs crudos** (`NetworkResult<OrderDto>`).
> Los métodos del **Repository** usan el prefijo **`get`** (ej. `getOrders()`, `getOrder(id)`) porque retornan **modelos de dominio** ya mapeados (`NetworkResult<Order>`).
> Esta distinción es intencional: indica al lector si está trabajando con datos técnicos (DTO) o datos de negocio (modelo de dominio).

### Paso 5: Crear el repository (capa data)

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

> **Nota:** El repository expone `getOrders()` (no `fetchOrders()`). Esto señala que el consumidor recibe modelos de dominio (`Order`), no DTOs. Internamente, delega al DataSource (`fetchOrders()`) y aplica el mapper.

### Paso 6: Cablear todo junto (ensamblaje)

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

Todo método del repository retorna `NetworkResult<T>`, que siempre es `Success` o `Failure` — nunca lanza excepciones. A continuación se muestran los patrones más comunes para consumirlo.

### Escenario: Mostrar datos o un mensaje de error

Tu pantalla necesita cargar una lista de órdenes. Si la carga es exitosa, muestra los datos; si falla, muestra el mensaje de error al usuario. `fold` te obliga a cubrir ambos casos — es el patrón recomendado.

```kotlin
repository.getOrders().fold(
    onSuccess = { orders -> /* modelos de dominio limpios */ },
    onFailure = { error -> /* error.message es seguro para el usuario */ }
)
```

### Escenario: Encadenar dos llamadas dependientes

Para mostrar el detalle de un pago, primero necesitas obtener la orden y luego usar su `paymentId` para obtener el pago. `flatMap` encadena dos operaciones donde la segunda depende del resultado de la primera — si la primera falla, la segunda nunca se ejecuta.

```kotlin
val result: NetworkResult<PaymentInfo> = repository.getOrder(orderId)
    .flatMap { order -> paymentRepository.getPayment(order.paymentId) }
```

### Escenario: Transformar datos antes de mostrarlos

Obtuviste el detalle de una orden, pero necesitas un string formateado para mostrar en la UI. `map` transforma los datos exitosos sin afectar el caso de error.

```kotlin
val formatted: NetworkResult<String> = repository.getOrder(orderId)
    .map { order -> "${order.status}: ${order.totalAmount}" }
```

### Escenario: Ejecutar acciones secundarias sin alterar el resultado

Al cargar las órdenes quieres registrar un evento de analytics en éxito, o loguear el error en fallo, pero sin modificar el resultado. `onSuccess` y `onFailure` ejecutan side effects y retornan el mismo `NetworkResult`, permitiendo encadenar con `fold` después.

```kotlin
repository.getOrders()
    .onSuccess { orders -> analytics.track("orders_loaded", orders.size) }
    .onFailure { error -> logger.warn("Failed to load orders: ${error.diagnostic?.description}") }
    .fold(
        onSuccess = { /* usar datos */ },
        onFailure = { /* manejar error */ }
    )
```

### Escenario: Verificar rápidamente si hay datos disponibles

En un test unitario, o en lógica donde solo necesitas saber si los datos existen sin manejar el error en detalle, puedes extraer valores directamente.

```kotlin
val orders: List<Order>? = repository.getOrders().getOrNull()
val error: NetworkError? = repository.getOrders().errorOrNull()

if (result.isSuccess) { /* ... */ }
if (result.isFailure) { /* ... */ }
```

> **Recomendación:** Prefiere `.fold()` para manejo exhaustivo. Usa `.getOrNull()` solo en tests o cuando genuinamente no te importa el caso de fallo.

### Escenario: Inspeccionar metadata de la respuesta

Tu app quiere medir la latencia percibida, mostrar cuántos reintentos se necesitaron, o leer un header custom del servidor (ej. `X-Request-Id` para soporte). Cuando una request es exitosa, `NetworkResult.Success` incluye `ResponseMetadata` con esta información.

```kotlin
val result = repository.getOrders()
if (result is NetworkResult.Success) {
    val metadata = result.metadata
    println("Status: ${metadata.statusCode}")       // ej. 200
    println("Duración: ${metadata.durationMs}ms")    // ej. 142
    println("Intentos: ${metadata.attemptCount}")    // ej. 1 (sin retry) o 2+ (con retry)
    println("Request ID: ${metadata.requestId}")     // ej. "abc-123" (si el servidor lo envía)
    println("Headers: ${metadata.headers}")          // headers de respuesta
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `statusCode` | `Int` | Código HTTP de la respuesta (ej. 200, 201) |
| `headers` | `Map<String, List<String>>` | Headers de la respuesta del servidor |
| `durationMs` | `Long` | Duración total de la request en milisegundos |
| `requestId` | `String?` | ID de request del servidor (si lo envía en headers) |
| `attemptCount` | `Int` | Número de intentos (1 = sin retry, 2+ = con retry) |

### Referencia rápida: métodos de `NetworkResult<T>`

| Método | Retorna | Descripción |
|---|---|---|
| `.fold(onSuccess, onFailure)` | `R` | Manejo exhaustivo — siempre evalúa exactamente un branch |
| `.map { transform }` | `NetworkResult<R>` | Transforma datos exitosos, propaga errores sin cambios |
| `.flatMap { transform }` | `NetworkResult<R>` | Encadena operaciones que retornan `NetworkResult` |
| `.onSuccess { action }` | `NetworkResult<T>` | Ejecuta side effect en éxito, retorna el mismo resultado |
| `.onFailure { action }` | `NetworkResult<T>` | Ejecuta side effect en fallo, retorna el mismo resultado |
| `.getOrNull()` | `T?` | Extrae datos o `null` — solo para tests o cuando el fallo no importa |
| `.errorOrNull()` | `NetworkError?` | Extrae error o `null` |
| `.isSuccess` | `Boolean` | `true` si es `Success` |
| `.isFailure` | `Boolean` | `true` si es `Failure` |

---

## RequestContext — Trazabilidad y Control por Request

`RequestContext` te permite agregar metadata a cada request individual. Es opcional — si no lo necesitas, simplemente no lo pases.

### Cuándo usar RequestContext

- **Trazabilidad distribuida** — correlacionar requests con spans/traces.
- **Override de retry por request** — deshabilitar retry para una operación específica.
- **Tags custom** — agregar metadata para métricas o logging.
- **Control de auth** — indicar si una request requiere autenticación.

### Escenarios de uso

`RequestContext` se pasa como segundo argumento a `execute()` en el data source. Todos los ejemplos siguientes van dentro de un `RemoteDataSource`.

#### Escenario: Correlacionar una request con un trace distribuido

Tu sistema de observabilidad (Datadog, New Relic, etc.) genera un `traceId` por cada acción del usuario. Quieres que las requests HTTP se correlacionen con ese trace para poder ver el flujo completo en tu dashboard.

```kotlin
suspend fun fetchOrders(traceId: String): NetworkResult<List<OrderDto>> = execute(
    request = HttpRequest(path = "/orders", method = HttpMethod.GET),
    context = RequestContext(
        operationId = "fetch-orders",
        parentSpanId = traceId,
        tags = mapOf("source" to "home")
    ),
    deserialize = { json.decodeFromString(it.body!!.decodeToString()) }
)
```

- **`operationId`** — identifica la operación en logs y métricas. Los observers lo reciben para generar tags.
- **`parentSpanId`** — correlaciona esta request con un span padre en tu sistema de tracing.
- **`tags`** — metadata arbitraria que los observers pueden leer (ej. desde qué pantalla se hizo la llamada).

#### Escenario: Deshabilitar retry para una operación de pago

Tu executor global tiene `RetryPolicy.ExponentialBackoff(maxRetries = 3)`, lo cual es correcto para GETs. Pero para crear un pago, reintentar podría cobrar dos veces al usuario. Necesitas deshabilitar retry solo para esta request.

```kotlin
suspend fun createPayment(dto: CreatePaymentDto): NetworkResult<PaymentDto> = execute(
    request = HttpRequest(
        path = "/payments",
        method = HttpMethod.POST,
        headers = mapOf("Content-Type" to "application/json"),
        body = json.encodeToString(dto).encodeToByteArray()
    ),
    context = RequestContext(
        operationId = "create-payment",
        retryPolicyOverride = RetryPolicy.None
    ),
    deserialize = { json.decodeFromString(it.body!!.decodeToString()) }
)
```

- **`retryPolicyOverride`** — si no es `null`, reemplaza la política de `NetworkConfig` para esta request específica.

#### Escenario: Señalar que una request requiere autenticación

Tu app tiene endpoints públicos (catálogo de productos) y endpoints privados (datos del usuario). Quieres que un interceptor auth-aware solo agregue el header `Authorization` en las requests que lo necesitan, sin inyectarlo en todas.

```kotlin
suspend fun fetchSecureData(): NetworkResult<SecureDto> = execute(
    request = HttpRequest(path = "/secure/data", method = HttpMethod.GET),
    context = RequestContext(
        operationId = "fetch-secure-data",
        requiresAuth = true
    ),
    deserialize = { json.decodeFromString(it.body!!.decodeToString()) }
)
```

- **`requiresAuth`** — los interceptors de auth pueden leer este flag para decidir si inyectar credenciales o no.

### Campos de `RequestContext`

| Campo | Tipo | Default | Descripción |
|---|---|---|---|
| `operationId` | `String` | (requerido) | Identificador de la operación para logs/métricas/traces |
| `tags` | `Map<String, String>` | `emptyMap()` | Metadata custom — los observers pueden leerla |
| `retryPolicyOverride` | `RetryPolicy?` | `null` | Si no es `null`, reemplaza la política de `NetworkConfig` para esta request |
| `parentSpanId` | `String?` | `null` | ID del span padre para trazabilidad distribuida |
| `requiresAuth` | `Boolean` | `false` | Señal para interceptors — indica si esta request necesita credenciales |

---

## ResponseInterceptor — Post-procesamiento de Respuestas

Los `ResponseInterceptor` se ejecutan **después** del transporte HTTP pero **antes** de la validación y deserialización. Son útiles para:

- Extraer headers de respuesta (ej. tokens de paginación, rate limits).
- Transformar respuestas antes de que lleguen al data source.
- Cachear respuestas condicionalmente.

#### Escenario: Rastrear el rate limit del API

Tu API envía headers `X-RateLimit-Remaining` y `X-RateLimit-Reset` en cada respuesta. Quieres extraer estos valores para mostrar un aviso al usuario cuando se acerque al límite, o para implementar throttling preventivo.

```kotlin
import com.dancr.platform.network.execution.ResponseInterceptor

val rateLimitInterceptor = ResponseInterceptor { response, request, context ->
    val remaining = response.headers["X-RateLimit-Remaining"]?.firstOrNull()
    remaining?.let { rateLimitTracker.update(it.toInt()) }
    response  // siempre retorna la respuesta (modificada o no)
}
```

#### Escenario: Extraer el ID de request del servidor para soporte

Cuando un usuario reporta un error, necesitas el `X-Request-Id` que el servidor asignó a esa request para poder buscarlo en los logs del backend. Este interceptor lo extrae de cada respuesta y lo guarda.

```kotlin
val requestIdInterceptor = ResponseInterceptor { response, request, context ->
    val requestId = response.headers["X-Request-Id"]?.firstOrNull()
    requestId?.let { supportTracker.storeLastRequestId(it) }
    response
}
```

#### Cablear interceptors al executor

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    responseInterceptors = listOf(rateLimitInterceptor, requestIdInterceptor)
)
```

> **Nota:** El logging de respuestas se maneja vía `LoggingObserver` (sección Observabilidad), no vía `ResponseInterceptor`. Usa interceptors para lógica de infraestructura, no para logging.

---

## Agregar Autenticación

El SDK soporta múltiples estrategias de autenticación. Esta sección cubre desde el caso más simple (API Key estática) hasta el flujo completo de sesión con refresh de tokens.

### Tipos de credencial disponibles

El SDK incluye 4 tipos de credencial listos para usar. `CredentialHeaderMapper` los convierte automáticamente a headers HTTP:

| Tipo | Ejemplo | Header Generado |
|---|---|---|
| `Credential.Bearer(token)` | `Credential.Bearer("eyJhb...")` | `Authorization: Bearer eyJhb...` |
| `Credential.ApiKey(key, headerName)` | `Credential.ApiKey("abc123", "X-API-Key")` | `X-API-Key: abc123` |
| `Credential.Basic(username, password)` | `Credential.Basic("user", "pass")` | `Authorization: Basic dXNlcjpwYXNz` |
| `Credential.Custom(type, properties)` | `Credential.Custom("OAuth", mapOf("X-Token" to "..."))` | Cada entrada de `properties` se convierte en un header |

### Opción A: Autenticación simple (API Key o token fijo)

Si tu API usa una API Key o un token que no cambia, implementa `CredentialProvider` directamente:

```kotlin
import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.credential.CredentialProvider

// API Key estática
class ApiKeyProvider(private val apiKey: String) : CredentialProvider {
    override suspend fun current(): Credential = Credential.ApiKey(apiKey, "X-API-Key")
}

// Token Bearer fijo (ej. service-to-service)
class FixedTokenProvider(private val token: String) : CredentialProvider {
    override suspend fun current(): Credential = Credential.Bearer(token)
}

// Basic Auth
class BasicAuthProvider(
    private val username: String,
    private val password: String
) : CredentialProvider {
    override suspend fun current(): Credential = Credential.Basic(username, password)
}
```

### Opción B: Autenticación con sesión completa (token + refresh)

Para apps con login de usuario, usa el flujo completo del SDK: `SecretStore` → `DefaultSessionController` → `DefaultCredentialProvider`.

#### Paso 1: Configurar almacenamiento seguro

```kotlin
// Android — usa AndroidSecretStore (DataStore + Cipher + Keystore)
import com.dancr.platform.security.store.AndroidSecretStore
import com.dancr.platform.security.store.AndroidStoreConfig

val secretStore = AndroidSecretStore(
    config = AndroidStoreConfig(
        dataStoreName = "my_app_secure_store",
        keyAlias = "_my_app_crypto_key_"
    ),
    context = applicationContext  // Android Context
)

// iOS — usa IosSecretStore (Keychain Services)
import com.dancr.platform.security.store.IosSecretStore
import com.dancr.platform.security.store.KeychainConfig

val secretStore = IosSecretStore(
    config = KeychainConfig(serviceName = "com.myapp.secrets")
)
```

#### Paso 2: Crear el SessionController

`DefaultSessionController` gestiona el ciclo de vida de la sesión: inicio, refresh, cierre, e invalidación.

```kotlin
import com.dancr.platform.security.session.DefaultSessionController
import com.dancr.platform.security.session.SessionCredentials

val sessionController = DefaultSessionController(
    store = secretStore,
    // refreshTokenProvider: función que recibe el refresh token actual
    // y retorna nuevas credenciales, o null si el refresh falla.
    refreshTokenProvider = { refreshToken ->
        // Llama a tu endpoint de refresh (sin usar el SDK para evitar dependencia circular)
        val response = myAuthClient.refreshToken(refreshToken)
        if (response != null) {
            SessionCredentials(
                credential = Credential.Bearer(response.accessToken),
                refreshToken = response.refreshToken,
                expiresAtMs = response.expiresAtMs
            )
        } else null
    }
)
```

#### Paso 3: Crear el CredentialProvider

`DefaultCredentialProvider` conecta el `SessionController` con el interceptor de auth:

```kotlin
import com.dancr.platform.security.credential.DefaultCredentialProvider

val credentialProvider = DefaultCredentialProvider(sessionController)
```

#### Paso 4: Construir el interceptor de auth

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

#### Paso 5: Cablear al executor

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    interceptors = listOf(authInterceptor(credentialProvider))
)
```

### Ciclo de vida de la sesión

Una vez configurado, el `SessionController` gestiona las transiciones de sesión. Cada operación se muestra con su escenario de uso.

#### Escenario: El usuario completa el login

Después de que tu pantalla de login recibe la respuesta del servidor con los tokens, inicias la sesión en el SDK. Esto persiste las credenciales en el `SecretStore` y cambia el estado a `Active`.

```kotlin
import com.dancr.platform.security.session.SessionCredentials

sessionController.startSession(
    SessionCredentials(
        credential = Credential.Bearer(loginResponse.accessToken),
        refreshToken = loginResponse.refreshToken,
        expiresAtMs = loginResponse.expiresAtMs  // opcional
    )
)
```

#### Escenario: Verificar si el usuario tiene sesión activa

Al abrir la app, necesitas decidir si mostrar la pantalla principal o la de login. Consulta el estado actual de la sesión.

```kotlin
import com.dancr.platform.security.session.SessionState

when (sessionController.state.value) {
    is SessionState.Active -> { /* sesión activa — mostrar pantalla principal */ }
    is SessionState.Idle -> { /* sin sesión — redirigir a login */ }
    is SessionState.Expired -> { /* token expirado — intentar refresh o re-login */ }
}
```

#### Escenario: Renovar el token antes de que expire

Tu app detecta que el token está próximo a expirar (ej. por un timer o antes de una operación crítica). Disparas un refresh proactivo para evitar que la siguiente request falle con 401.

```kotlin
import com.dancr.platform.security.session.RefreshOutcome

when (val outcome = sessionController.refreshSession()) {
    is RefreshOutcome.Refreshed -> { /* nuevo token obtenido — las requests siguientes lo usarán */ }
    is RefreshOutcome.NotNeeded -> { /* no hay refresh token configurado — estado sin cambios */ }
    is RefreshOutcome.Failed -> { /* refresh falló — redirigir a login */ }
}
```

#### Escenario: El usuario hace logout voluntariamente

El usuario presiona "Cerrar sesión" en la configuración de tu app. `endSession()` borra las credenciales del `SecretStore` y emite `SessionEvent.Ended`.

```kotlin
sessionController.endSession()
```

#### Escenario: Invalidar la sesión por seguridad

Tu app detecta un 401 inesperado, un posible compromiso de seguridad, o una violación de integridad. `invalidate()` borra las credenciales inmediatamente y emite `SessionEvent.Invalidated`. La diferencia con `endSession()` es semántica: `invalidate` indica que la sesión fue forzada a terminar, no que el usuario lo pidió.

```kotlin
sessionController.invalidate()
```

### Escenario: Manejar un 401 en la capa de app

El servidor rechaza una request con `401 Unauthorized` porque el token expiró. El SDK clasifica esto como `NetworkError.Authentication` pero **no** hace refresh automático — eso es responsabilidad de tu capa de app. El patrón recomendado es intentar un refresh y, si falla, forzar re-login.

```kotlin
repository.getOrders().onFailure { error ->
    if (error is NetworkError.Authentication) {
        when (val outcome = sessionController.refreshSession()) {
            is RefreshOutcome.Refreshed -> {
                // Token renovado — reintentar la request original
                repository.getOrders()  // segunda llamada usa el nuevo token
            }
            is RefreshOutcome.Failed,
            is RefreshOutcome.NotNeeded -> {
                // Refresh falló — forzar re-login
                sessionController.invalidate()
                navigateToLogin()
            }
        }
    }
}
```

### Escenario: Reaccionar a cambios de sesión en la UI

Tu app necesita actualizar la UI cuando la sesión cambia — por ejemplo, ocultar el botón de "Mi cuenta" si la sesión se invalidó, o mostrar un banner "Sesión expirada". `SessionController` expone dos Flows: `state` (estado continuo) y `events` (eventos discretos).

```kotlin
import com.dancr.platform.security.session.SessionEvent

// Observar cambios de estado (StateFlow — siempre tiene un valor actual)
sessionController.state.collect { state ->
    when (state) {
        is SessionState.Active -> { /* mostrar UI autenticada */ }
        is SessionState.Idle -> { /* mostrar UI de login */ }
        is SessionState.Expired -> { /* mostrar banner "Sesión expirada" */ }
    }
}

// Observar eventos discretos (Flow — solo se emiten cuando algo pasa)
sessionController.events.collect { event ->
    when (event) {
        is SessionEvent.Started -> { /* sesión iniciada — navegar a home */ }
        is SessionEvent.Refreshed -> { /* token renovado silenciosamente */ }
        is SessionEvent.Ended -> { /* logout exitoso — navegar a login */ }
        is SessionEvent.Invalidated -> { /* sesión forzada a terminar — navegar a login */ }
        is SessionEvent.RefreshFailed -> { /* refresh falló: ${event.error} */ }
    }
}
```

---

## Certificate Pinning

Certificate Pinning protege contra ataques Man-in-the-Middle (MITM) verificando que el certificado del servidor coincida con un hash conocido.

### Escenario: Tu app maneja datos sensibles y necesitas protección contra proxies maliciosos

Tu app de banca o salud transmite datos sensibles. Un atacante con acceso a la red WiFi del usuario podría interceptar el tráfico con un certificado falso. Con certificate pinning, la app solo acepta conexiones con certificados cuyo hash coincida con los que tú configuraste.

```kotlin
import com.dancr.platform.security.trust.DefaultTrustPolicy
import com.dancr.platform.security.trust.CertificatePin

val trustPolicy = DefaultTrustPolicy(
    pins = mapOf(
        "api.yourcompany.com" to setOf(
            // Pin primario (certificado actual)
            CertificatePin(algorithm = "sha256", hash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
            // Pin de respaldo (certificado siguiente — OBLIGATORIO)
            CertificatePin(algorithm = "sha256", hash = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
        ),
        "auth.yourcompany.com" to setOf(
            CertificatePin(algorithm = "sha256", hash = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
        )
    )
)

// Pasar al engine al crearlo
val engine = KtorHttpEngine.create(config, trustPolicy)
```

> **Importante:** Siempre incluye al menos un **pin de respaldo** por dominio. Si el certificado rota y solo tienes un pin, tu app dejará de conectarse. El flujo seguro es: (1) publicar app con pin de respaldo del nuevo certificado, (2) rotar certificado en el servidor.

### Sin pinning (desarrollo)

Para entornos de desarrollo, simplemente no pases `TrustPolicy`:

```kotlin
// Sin pinning — solo para dev/debug
val engine = KtorHttpEngine.create(config)

// Con pinning — producción
val engine = KtorHttpEngine.create(config, trustPolicy)
```

---

## Agregar Observabilidad

Los observers se ejecutan durante el ciclo de vida de cada request. No modifican la request ni la respuesta — solo observan. El SDK incluye implementaciones listas para usar y permite crear observers custom.

### Callbacks de `NetworkEventObserver`

| Callback | Cuándo se invoca |
|---|---|
| `onRequestStarted(request, context)` | Antes de enviar la request (después de interceptors) |
| `onResponseReceived(request, response, durationMs, context)` | Respuesta HTTP recibida (cualquier status code) |
| `onRetryScheduled(request, attempt, maxAttempts, error, delayMs)` | Antes de cada reintento |
| `onRequestFailed(request, error, durationMs, context)` | Request falló (error de red, validación, o deserialización) |

Todos los métodos tienen implementaciones no-op por defecto — solo sobreescribe los que necesites.

### Escenario: Ver en consola qué requests hace tu app

Durante el desarrollo, quieres ver en Logcat/consola cada request que hace tu app, su resultado y cuánto tardó. El SDK incluye `LoggingObserver` que formatea estos eventos y los delega a tu backend de logging (Timber, Logcat, OSLog, etc.).

```kotlin
import com.dancr.platform.network.observability.LoggingObserver
import com.dancr.platform.network.observability.NetworkLogger

// 1. Implementa NetworkLogger con tu backend de logging
val myLogger = object : NetworkLogger {
    override fun log(level: NetworkLogger.Level, tag: String, message: String) {
        when (level) {
            NetworkLogger.Level.DEBUG -> Log.d(tag, message)   // o tu logger
            NetworkLogger.Level.INFO -> Log.i(tag, message)
            NetworkLogger.Level.WARN -> Log.w(tag, message)
            NetworkLogger.Level.ERROR -> Log.e(tag, message)
        }
    }
}

// 2. Crea el LoggingObserver
val loggingObserver = LoggingObserver(
    logger = myLogger,
    tag = "MyApp-Network",
    // Por defecto, TODOS los valores de headers se redactan con ██ (OWASP MASVS-PRIVACY).
    // Si quieres visibilidad selectiva, provee tu propio sanitizer:
    headerSanitizer = { key, value ->
        if (key.equals("Authorization", ignoreCase = true)) "██"
        else value  // mostrar headers no sensibles
    }
)
```

**Output de LoggingObserver:**
```
DEBUG  --> GET /orders [Accept: application/json, Authorization: ██]
INFO   <-- 200 GET /orders (142ms)
WARN   ⟳ Retry 1/3 for GET /orders after 1000ms — Unable to reach the server
ERROR  FAILED GET /orders (5023ms) — The request timed out
```

### Escenario: Enviar métricas de latencia y errores a tu sistema de monitoreo

Tu equipo usa Datadog, Prometheus, o un sistema de métricas interno. Quieres registrar la latencia de cada request HTTP, la tasa de errores por tipo, y la frecuencia de reintentos. Crea un observer custom que delegue a tu cliente de métricas.

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
                "status" to response.statusCode.toString(),
                "operation" to (context?.operationId ?: "unknown")
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

Puedes combinar múltiples observers. Se ejecutan en orden:

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    observers = listOf(
        loggingObserver,                // logging built-in
        AppMetricsObserver(myMetrics),  // métricas custom
        // Puedes agregar más: tracing, analytics, etc.
    )
)
```

> **Nota:** El SDK también incluye `MetricsObserver` (requiere implementar `MetricsCollector`) y `TracingObserver` (requiere implementar `TracingBackend`) como observers built-in para métricas y trazabilidad distribuida respectivamente. Consulta los READMEs de `network-core/` para detalles.

---

## Configuración Multi-Entorno

### Escenario: Tu app tiene ambientes de desarrollo, staging y producción

Cada ambiente apunta a una URL distinta y tiene políticas de retry diferentes (en dev quieres fallo rápido para detectar errores; en producción quieres resiliencia). Define un objeto con las configuraciones y selecciona por build flavor o variable de entorno:

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

### Escenario: Tu pantalla necesita reaccionar diferente según el tipo de error

Una pantalla de detalle de orden necesita: mostrar un banner de "sin conexión" con botón retry para errores de red, redirigir a login si el token expiró, y mostrar un error genérico para todo lo demás. El siguiente patrón cubre todos los tipos de error:

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

### Escenario: Pantalla simple donde solo necesitas mostrar éxito o error

En una pantalla secundaria (ej. "Acerca de" con versión del servidor), no necesitas manejo granular por tipo de error — solo mostrar datos o un mensaje genérico:

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
fun `fetchOrders returns DTOs on success`() = runTest {
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
    suspend fun fetchItems() = execute(
        request = HttpRequest(path = "/items"),
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
