# Guía Rápida para Android

**Cómo usar el Core Data Platform SDK si vienes de Android y no tienes contexto previo.**

---

## ¿Qué es esto?

Es un SDK interno que te da networking y seguridad listos para usar. En vez de configurar Retrofit/Ktor directamente, manejar errores manualmente, o escribir lógica de retry en cada pantalla, este SDK te da:

- **Llamadas HTTP** con retry automático, clasificación de errores, y timeouts configurables.
- **Manejo de credenciales** (Bearer token, API Key, Basic Auth) sin que tú toques headers.
- **Un resultado tipado** (`NetworkResult<T>`) que es `Success` o `Failure` — nunca excepciones sueltas.

Tú solo interactúas con **repositorios** que devuelven modelos de dominio limpios. Todo lo demás (transporte HTTP, serialización, retry, headers de auth) sucede por debajo.

---

## Estructura mental: 3 cosas que necesitas saber

```
1. NetworkConfig     → Configuras: URL base, timeouts, reintentos
2. Factory.create()  → Creas: te devuelve un Repository listo para usar
3. repository.get()  → Consumes: recibes NetworkResult<T> con tus modelos
```

Eso es todo. No necesitas saber nada más para empezar.

---

## Ejemplo completo: de cero a datos en pantalla

### Paso 1: Obtener el repository

```kotlin
import com.dancr.platform.sample.di.SampleApiFactory

// Una línea. Ya tienes un repository funcional.
val userRepository = SampleApiFactory.create()
```

`SampleApiFactory.create()` internamente:
- Crea el cliente HTTP (Ktor)
- Configura timeouts (15s connect, 30s read)
- Configura retry con exponential backoff (2 reintentos)
- Arma todo el pipeline de ejecución
- Te devuelve un `UserRepository` listo

**Tú no ves nada de eso.** Solo recibes el repository.

### Paso 2: Llamar desde tu ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.sample.model.User
import com.dancr.platform.sample.di.SampleApiFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserViewModel : ViewModel() {

    private val repository = SampleApiFactory.create()

    private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Loading)
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading

            repository.getUsers().fold(
                onSuccess = { users ->
                    _uiState.value = UserUiState.Success(users)
                },
                onFailure = { error ->
                    _uiState.value = UserUiState.Error(error.message)
                }
            )
        }
    }
}

sealed interface UserUiState {
    data object Loading : UserUiState
    data class Success(val users: List<User>) : UserUiState
    data class Error(val message: String) : UserUiState
}
```

### Paso 3: Mostrar en Compose

```kotlin
@Composable
fun UserScreen(viewModel: UserViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.loadUsers() }

    when (val s = state) {
        is UserUiState.Loading -> CircularProgressIndicator()
        is UserUiState.Success -> {
            LazyColumn {
                items(s.users) { user ->
                    Text("${user.displayName} (@${user.handle})")
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        is UserUiState.Error -> {
            Text("Error: ${s.message}")
            Button(onClick = { viewModel.loadUsers() }) {
                Text("Reintentar")
            }
        }
    }
}
```

**Eso es todo.** No configuraste Ktor, no parseaste JSON manualmente, no escribiste try-catch.

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

Si tu API requiere token, solo pasa un `CredentialProvider`:

```kotlin
import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.credential.CredentialProvider

// 1. Implementa CredentialProvider (una vez en tu app)
class MyAuthProvider(private val tokenManager: TokenManager) : CredentialProvider {
    override suspend fun current(): Credential? {
        val token = tokenManager.getAccessToken() ?: return null
        return Credential.Bearer(token)
    }
}

// 2. Pásalo al factory
val repository = SampleApiFactory.create(
    credentialProvider = MyAuthProvider(tokenManager)
)

// 3. Listo. Todas las requests llevan el header "Authorization: Bearer <token>"
```

El SDK se encarga de:
- Llamar a `current()` antes de cada request
- Convertir el `Credential` al header correcto automáticamente
- Si `current()` retorna `null`, la request va sin auth (no falla)

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

## Diagrama: ¿qué pasa cuando llamas `repository.getUsers()`?

```
Tu ViewModel
    │
    │  repository.getUsers()
    ▼
UserRepository
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
NetworkResult<List<UserDto>>
    │
    ▼  (de vuelta en UserRepository)
.map(UserMapper::toDomain)
    │
    ▼
NetworkResult<List<User>>  ← esto es lo que recibe tu ViewModel
```

**Lo importante:** tú solo ves la primera y la última línea. Todo lo del medio es transparente.

---

## Resumen: lo mínimo que necesitas recordar

| Quiero... | Hago... |
|---|---|
| Obtener datos | `repository.getUsers()` → devuelve `NetworkResult<List<User>>` |
| Manejar éxito/error | `.fold(onSuccess = { }, onFailure = { })` |
| Mostrar error al usuario | `error.message` (siempre seguro) |
| Agregar autenticación | Implementar `CredentialProvider` y pasarlo al factory |
| Cambiar URL/timeouts | Crear un `NetworkConfig` y pasarlo al factory |
| Transformar datos | `.map { }` sobre el `NetworkResult` |
| Encadenar llamadas | `.flatMap { }` para llamadas secuenciales |

---

## Preguntas frecuentes

**¿Necesito configurar Ktor, Retrofit, o algo de networking?**
No. El SDK lo maneja internamente. Tú solo usas repositories.

**¿Necesito hacer try-catch?**
No. `NetworkResult` captura todos los errores. Nunca te llega una excepción.

**¿Qué pasa si no hay internet?**
Recibes `NetworkResult.Failure(NetworkError.Connectivity(...))`. El SDK ya intentó reintentar si tenías retry configurado.

**¿Funciona con Hilt/Koin?**
Sí. El factory te da un repository que puedes registrar como singleton en tu DI:
```kotlin
// Hilt
@Provides @Singleton
fun provideUserRepository(): UserRepository = SampleApiFactory.create()

// Koin
single { SampleApiFactory.create() }
```

**¿Puedo ver los logs de las requests?**
Todavía no hay un `LoggingObserver` incluido, pero puedes crear uno implementando `NetworkEventObserver` y pasándolo al executor. Ver `docs/integration-guide.md` para detalles.

**¿Dónde está la documentación completa?**
- `docs/integration-guide.md` — Guía completa de integración
- `network-core/README.md` — Contratos y pipeline de ejecución
- `security-core/README.md` — Credenciales, sesiones, almacenamiento seguro
- `docs/diagrams/` — Diagramas de arquitectura (SVG)
