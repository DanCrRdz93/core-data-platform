# Integration Guide

**How to integrate the Core Data Platform SDK into your application**

This guide walks you through every step required to consume the SDK in a new or existing Kotlin Multiplatform application — from adding dependencies to making your first request to handling errors properly.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Adding Dependencies](#adding-dependencies)
- [Initial Configuration](#initial-configuration)
- [Building the Execution Pipeline](#building-the-execution-pipeline)
- [Creating a Domain API Module](#creating-a-domain-api-module)
- [Consuming Results](#consuming-results)
- [Adding Authentication](#adding-authentication)
- [Adding Observability](#adding-observability)
- [Multi-Environment Configuration](#multi-environment-configuration)
- [Error Handling in Detail](#error-handling-in-detail)
- [Architecture Recommendations](#architecture-recommendations)
- [Common Errors and Troubleshooting](#common-errors-and-troubleshooting)
- [What NOT to Do](#what-not-to-do)

---

## Prerequisites

### Tooling

| Requirement | Minimum Version | Notes |
|---|---|---|
| Kotlin | 2.1.20 | Kotlin Multiplatform plugin enabled |
| Gradle | 9.3.1 | Version catalog (`libs.versions.toml`) recommended |
| AGP | 9.1.0 | Uses `com.android.kotlin.multiplatform.library` plugin |
| Android Studio | Ladybug+ | KMP support required |
| Xcode | 15+ | For iOS compilation |
| Android `compileSdk` | 36 | |
| Android `minSdk` | 29 | Android 10+ |

### Knowledge

You should be familiar with:

- Kotlin coroutines (`suspend`, `Flow`, `StateFlow`)
- Kotlin Multiplatform source set model (`commonMain`, `androidMain`, `iosMain`)
- Kotlin sealed classes and `when` expressions
- Gradle multi-module projects

---

## Adding Dependencies

### Step 1: Register the SDK modules

If the SDK lives as a local composite build or included modules, add them to your `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
include(":network-core")
include(":network-ktor")
include(":security-core")
```

If the SDK is published to a Maven repository, use coordinates instead (replace with your actual group/version):

```kotlin
// build.gradle.kts of your domain module
dependencies {
    implementation("com.dancr.platform:network-core:1.0.0")
    implementation("com.dancr.platform:network-ktor:1.0.0")
    implementation("com.dancr.platform:security-core:1.0.0")
}
```

### Step 2: Add dependencies to your domain module

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
            // SDK modules
            implementation(project(":network-core"))
            implementation(project(":network-ktor"))
            implementation(project(":security-core"))

            // Required for coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Required for JSON serialization in your DTOs
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

### What each dependency provides

| Dependency | You get |
|---|---|
| `:network-core` | `HttpRequest`, `SafeRequestExecutor`, `NetworkResult`, `NetworkError`, `RemoteDataSource`, `NetworkConfig`, `RetryPolicy`, `RequestInterceptor`, `ResponseInterceptor`, `NetworkEventObserver` |
| `:network-ktor` | `KtorHttpEngine`, `KtorErrorClassifier` — the concrete transport |
| `:security-core` | `Credential`, `CredentialProvider`, `CredentialHeaderMapper`, `SessionController`, `SecretStore`, `TrustPolicy`, `LogSanitizer` |

> **Tip:** If your module does not need authentication or security features, you can skip `:security-core`. The networking stack works independently.

---

## Initial Configuration

### NetworkConfig

Every application starts by defining a `NetworkConfig` — the base configuration shared by all requests:

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

### Configuration parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `baseUrl` | `String` | *(required)* | Base URL prepended to all request paths. Must not be blank. |
| `defaultHeaders` | `Map<String, String>` | `emptyMap()` | Headers added to every request. Per-request headers override these. |
| `connectTimeout` | `Duration` | `30.seconds` | TCP connection timeout |
| `readTimeout` | `Duration` | `30.seconds` | Full request timeout (Ktor `requestTimeoutMillis`) |
| `writeTimeout` | `Duration` | `30.seconds` | Socket timeout (Ktor `socketTimeoutMillis`) |
| `retryPolicy` | `RetryPolicy` | `RetryPolicy.None` | Automatic retry strategy for retryable errors |

### Retry policy options

```kotlin
// No retries (default)
RetryPolicy.None

// Fixed delay between retries
RetryPolicy.FixedDelay(
    maxRetries = 3,
    delay = 2.seconds
)

// Exponential backoff
RetryPolicy.ExponentialBackoff(
    maxRetries = 3,
    initialDelay = 1.seconds,
    maxDelay = 30.seconds,
    multiplier = 2.0
)
```

---

## Building the Execution Pipeline

The execution pipeline is where all the pieces come together. Build it once per API configuration and share it across data sources.

### Minimal setup (no auth, no observability)

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

### Full setup (auth + observability)

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = KtorHttpEngine.create(networkConfig),
    config = networkConfig,
    classifier = KtorErrorClassifier(),
    interceptors = listOf(
        authInterceptor,           // injects Authorization header
        tracingInterceptor         // injects X-Trace-Id header
    ),
    responseInterceptors = listOf(
        loggingResponseInterceptor // logs response status
    ),
    observers = listOf(
        metricsObserver,           // records latency, error rates
        analyticsObserver          // tracks request patterns
    )
)
```

### Lifecycle management

The `KtorHttpEngine` wraps a Ktor `HttpClient`. You should:

1. **Create one engine per configuration** — not one per request.
2. **Call `engine.close()`** when the application is shutting down or the scope is cancelled.
3. If using a DI framework (Koin, Hilt), register the engine as a singleton scoped to the application lifecycle.

```kotlin
// In your DI module
single { KtorHttpEngine.create(networkConfig) }
single<SafeRequestExecutor> {
    DefaultSafeRequestExecutor(
        engine = get(),
        config = networkConfig,
        classifier = KtorErrorClassifier()
    )
}

// On app shutdown
onClose { get<HttpEngine>().close() }
```

---

## Creating a Domain API Module

Follow this layered structure for every API domain you integrate:

```
your-module/src/commonMain/kotlin/com/yourcompany/yourmodule/
├── dto/                  # Technical models matching JSON
├── model/                # Clean domain models
├── mapper/               # DTO → Domain conversion
├── datasource/           # Builds requests, delegates to executor
├── repository/           # Maps DTO results to domain results
└── di/                   # Factory wiring
```

### Step 1: Define DTOs

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

**Rules:**
- Always `@Serializable`.
- Use `@SerialName` when JSON key differs from Kotlin property name.
- Default values for optional fields (`= emptyList()`, `= null`).
- No business logic here — these are pure data carriers.

### Step 2: Define domain models

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

**Rules:**
- No `@Serializable`.
- Use your app's vocabulary, not the API's.
- Flatten nested structures if consumers don't need them.

### Step 3: Create a mapper

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

### Step 4: Build the data source

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

**Key points:**
- Extend `RemoteDataSource` — this gives you `protected fun execute()`.
- Inject `SafeRequestExecutor` — the interface, not the concrete class.
- Create `Json` once, reuse across methods.
- `ignoreUnknownKeys = true` protects against API evolution.
- Paths are relative — the executor prepends `baseUrl`.

### Step 5: Create the repository

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

The repository's sole job is mapping `NetworkResult<Dto>` → `NetworkResult<Model>` using `NetworkResult.map()`.

### Step 6: Wire it together

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

## Consuming Results

### In a ViewModel (Android)

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

### Chaining operations

```kotlin
// Sequential: fetch order, then fetch payment
val result: NetworkResult<PaymentInfo> = repository.getOrder(orderId)
    .flatMap { order -> paymentRepository.getPayment(order.paymentId) }

// Transform success data
val formatted: NetworkResult<String> = repository.getOrder(orderId)
    .map { order -> "${order.status}: ${order.totalAmount}" }
```

### Side effects

```kotlin
repository.getOrders()
    .onSuccess { orders -> analytics.track("orders_loaded", orders.size) }
    .onFailure { error -> logger.warn("Failed to load orders: ${error.diagnostic?.description}") }
    .fold(
        onSuccess = { /* update UI */ },
        onFailure = { /* show error */ }
    )
```

### Extracting raw values

```kotlin
// Nullable extraction (use sparingly)
val orders: List<Order>? = repository.getOrders().getOrNull()
val error: NetworkError? = repository.getOrders().errorOrNull()

// Boolean checks
if (result.isSuccess) { /* ... */ }
if (result.isFailure) { /* ... */ }
```

> **Recommendation:** Prefer `.fold()` for exhaustive handling. Use `.getOrNull()` only in tests or when you genuinely don't care about the failure case.

---

## Adding Authentication

### Step 1: Implement CredentialProvider

```kotlin
import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.credential.CredentialProvider

class TokenCredentialProvider(
    private val tokenStore: TokenStore  // your app's token management
) : CredentialProvider {

    override suspend fun current(): Credential? {
        val token = tokenStore.getAccessToken() ?: return null
        return Credential.Bearer(token)
    }
}
```

### Step 2: Build the auth interceptor

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

`CredentialHeaderMapper` handles all credential types automatically:

| Credential | Generated Header |
|---|---|
| `Credential.Bearer("abc")` | `Authorization: Bearer abc` |
| `Credential.ApiKey("key", "X-API-Key")` | `X-API-Key: key` |
| `Credential.Basic("user", "pass")` | `Authorization: Basic dXNlcjpwYXNz` |
| `Credential.Custom("OAuth", props)` | All entries in `props` as headers |

### Step 3: Wire into the executor

```kotlin
val executor = DefaultSafeRequestExecutor(
    engine = engine,
    config = config,
    classifier = KtorErrorClassifier(),
    interceptors = listOf(authInterceptor(myCredentialProvider))
)
```

---

## Adding Observability

### Implement NetworkEventObserver

Only override the callbacks you need — all methods have default no-op implementations:

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

### Wire observers

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

## Multi-Environment Configuration

Define per-environment configs and select at build time or startup:

```kotlin
object ApiConfigs {

    val development = NetworkConfig(
        baseUrl = "https://dev-api.yourcompany.com/v1",
        defaultHeaders = mapOf("Accept" to "application/json"),
        connectTimeout = 30.seconds,
        readTimeout = 60.seconds,
        retryPolicy = RetryPolicy.None  // fail fast in dev
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

// At initialization
val config = when (BuildConfig.FLAVOR) {
    "dev" -> ApiConfigs.development
    "staging" -> ApiConfigs.staging
    else -> ApiConfigs.production
}
val repository = OrderApiFactory.create(config, credentialProvider)
```

---

## Error Handling in Detail

### Error types and recommended UI responses

| Error | When it happens | Recommended UI |
|---|---|---|
| `Connectivity` | No internet, DNS failure, server unreachable | "Check your connection" + retry button |
| `Timeout` | Server too slow | "Server is slow, please try again" + retry button |
| `Cancelled` | User navigated away, scope cancelled | No UI — operation was intentionally cancelled |
| `Authentication` | 401 — token expired or invalid | Redirect to login, or trigger silent token refresh |
| `Authorization` | 403 — user lacks permission | "You don't have access to this feature" |
| `ClientError` | 4xx (other) — bad request | "Something went wrong" + log `diagnostic` |
| `ServerError` | 5xx — server-side failure | "We're experiencing issues" (auto-retried) |
| `Serialization` | Response body doesn't match DTO | "Something went wrong" + log `diagnostic` urgently (API contract mismatch) |
| `ResponseValidation` | Custom validator rejected a 2xx response | Depends on your validator's logic |
| `Unknown` | Unclassified exception | "Something went wrong" + log `diagnostic` |

### Pattern: exhaustive error handling

```kotlin
result.onFailure { error ->
    when (error) {
        is NetworkError.Connectivity -> showRetryBanner("No connection")
        is NetworkError.Timeout -> showRetryBanner("Request timed out")
        is NetworkError.Cancelled -> { /* ignore — user action */ }

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

    // Always log diagnostic internally (never show to user)
    error.diagnostic?.let { d ->
        logger.error("${error::class.simpleName}: ${d.description}", d.cause)
    }
}
```

### Pattern: simplified error handling

For screens that don't need per-type handling:

```kotlin
result.fold(
    onSuccess = { data -> showContent(data) },
    onFailure = { error -> showError(error.message) }
)
```

`error.message` is always user-safe and human-readable.

---

## Architecture Recommendations

### Do

- **One `NetworkConfig` per API base URL.** If your app talks to `api.example.com` and `auth.example.com`, create two configs and two executors.
- **One factory per domain.** `OrderApiFactory`, `PaymentApiFactory`, `UserApiFactory`. Each wires its own data source and repository.
- **Inject `SafeRequestExecutor`, never `DefaultSafeRequestExecutor`.** Code to the interface.
- **Use `RequestContext` for tracing.** Pass an `operationId` so you can correlate logs, metrics, and traces to specific operations.
- **Handle `NetworkError.Serialization` as a critical signal.** It means the API response doesn't match your DTOs. This is a deployment issue, not a transient error.
- **Keep mappers pure.** No I/O, no state, no side effects. Just input → output.
- **Create `Json` once per data source.** Not per request.

```kotlin
// ✅ Good
class MyDataSource(executor: SafeRequestExecutor) : RemoteDataSource(executor) {
    private val json = Json { ignoreUnknownKeys = true }
    // ... use json in every method
}

// ❌ Bad
suspend fun fetchData(): NetworkResult<Data> = execute(
    request = HttpRequest(...),
    deserialize = { response ->
        val json = Json { ignoreUnknownKeys = true } // wasteful!
        json.decodeFromString(response.body!!.decodeToString())
    }
)
```

### Test strategy

```kotlin
// Mock the executor for data source tests
class FakeExecutor(private val result: NetworkResult<*>) : SafeRequestExecutor {
    override suspend fun <T> execute(
        request: HttpRequest,
        context: RequestContext?,
        deserialize: (RawResponse) -> T
    ): NetworkResult<T> = result as NetworkResult<T>
}

// Test the data source
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

## Common Errors and Troubleshooting

### 1. `Unresolved reference: KtorHttpEngine`

**Cause:** Missing `:network-ktor` dependency.

**Fix:** Add `implementation(project(":network-ktor"))` to your module's `commonMain.dependencies`.

### 2. `Unresolved reference: Json` or `@Serializable`

**Cause:** Missing serialization plugin or dependency.

**Fix:**
```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.serialization)
}
// commonMain.dependencies
implementation(libs.kotlinx.serialization.json)
```

### 3. `kotlinx.serialization.json.internal.JsonDecodingException: Unexpected JSON token`

**Cause:** Response body doesn't match your DTO structure.

**Fix:** Verify your `@Serializable` data class matches the actual API response. Use `ignoreUnknownKeys = true` on your `Json` instance to tolerate new fields.

### 4. `NetworkError.Serialization` on every request

**Cause:** Often a null body (`response.body!!` force-unwrap failing). The API might return 204 No Content or an empty body.

**Fix:** Guard against null body in your deserialize lambda:

```kotlin
deserialize = { response ->
    val bodyString = response.body?.decodeToString()
        ?: throw IllegalStateException("Empty response body")
    json.decodeFromString(bodyString)
}
```

### 5. `NetworkError.Connectivity` when device has internet

**Cause:** Incorrect `baseUrl` in `NetworkConfig`, or the server is down.

**Fix:** Check `error.diagnostic?.description` for the underlying exception message. Verify the base URL is correct and reachable.

### 6. Duplicate `Content-Type` header error from Ktor

**Cause:** Setting `Content-Type` in both `HttpRequest.headers` and via the body.

**Fix:** `KtorHttpEngine` already extracts `Content-Type` from your headers and passes it to the body. Just set it in your `HttpRequest.headers`:

```kotlin
HttpRequest(
    path = "/orders",
    method = HttpMethod.POST,
    headers = mapOf("Content-Type" to "application/json"),
    body = jsonString.encodeToByteArray()
)
```

### 7. Auth headers not appearing in requests

**Cause:** `CredentialProvider.current()` returns `null`, or the auth interceptor is not in the interceptors list.

**Fix:** Verify your `CredentialProvider` returns a non-null `Credential`, and that the interceptor is passed to `DefaultSafeRequestExecutor`.

### 8. Retries not happening

**Cause:** `RetryPolicy.None` is the default. Or the error type is not retryable.

**Fix:** Set a retry policy in `NetworkConfig`, and verify that the error type has `isRetryable = true`. Only `Connectivity`, `Timeout`, and `ServerError` are retryable by default.

---

## What NOT to Do

### ❌ Don't import Ktor types in domain modules

```kotlin
// WRONG — leaks transport details
import io.ktor.client.statement.HttpResponse

class MyDataSource {
    suspend fun fetch(): HttpResponse { ... }
}
```

Use `HttpRequest`, `RawResponse`, and `SafeRequestExecutor` exclusively.

### ❌ Don't create an HttpEngine per request

```kotlin
// WRONG — creates a new HTTP client on every call
suspend fun fetchData() {
    val engine = KtorHttpEngine.create(config)  // expensive!
    executor.execute(...)
    engine.close()
}
```

Create one engine at startup, share it.

### ❌ Don't catch NetworkResult.Failure as an exception

```kotlin
// WRONG — NetworkResult is a value, not an exception
try {
    val result = repository.getOrders()
} catch (e: NetworkResult.Failure) {  // This doesn't compile
    ...
}
```

Use `.fold()`, `.onFailure()`, or `when (result)`.

### ❌ Don't show `diagnostic` to users

```kotlin
// WRONG — diagnostic contains stack traces and internal details
showToast(error.diagnostic?.description ?: "Error")

// RIGHT — message is always user-safe
showToast(error.message)
```

### ❌ Don't put business logic in interceptors

```kotlin
// WRONG — interceptors are for infrastructure
val priceValidator = RequestInterceptor { request, _ ->
    if (calculateTotal(request) > MAX_ORDER) throw TooExpensiveException()
    request
}

// RIGHT — put business logic in the repository or use case
class OrderRepository {
    suspend fun placeOrder(order: Order): NetworkResult<Confirmation> {
        require(order.total <= MAX_ORDER) { "Order exceeds limit" }
        return dataSource.createOrder(order.toDto())
    }
}
```

### ❌ Don't depend on `network-core` from `security-core` (or vice versa)

These modules are intentionally independent. If you need to bridge them (e.g., invalidate session on 401), do it in the consuming layer:

```kotlin
// In your app module — not in the SDK
repository.getOrders().onFailure { error ->
    if (error is NetworkError.Authentication) {
        sessionController.endSession()
    }
}
```

### ❌ Don't hardcode base URLs in data sources

```kotlin
// WRONG
class MyDataSource {
    private val baseUrl = "https://api.prod.example.com"
}

// RIGHT — base URL comes from NetworkConfig
class MyDataSource(executor: SafeRequestExecutor) : RemoteDataSource(executor) {
    // path is relative; executor prepends baseUrl
    suspend fun fetch() = execute(
        request = HttpRequest(path = "/data"),
        deserialize = { ... }
    )
}
```

### ❌ Don't share a single executor across different base URLs

```kotlin
// WRONG — both APIs use the same baseUrl from one config
val executor = DefaultSafeRequestExecutor(engine, configForApiA, ...)
val repoA = OrderRepository(OrderDataSource(executor))    // correct baseUrl
val repoB = PaymentRepository(PaymentDataSource(executor)) // wrong baseUrl!
```

Create separate configs and executors for different APIs.

---

*For more details, see the module-level READMEs in `network-core/`, `network-ktor/`, `security-core/`, and `sample-api/`.*
