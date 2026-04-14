# Cómo usar el SDK en un proyecto con Clean Architecture

**Guía para integrar Core Data Platform en la capa `data` de un proyecto Android que sigue Clean Architecture.**

---

## Tu estructura actual (probablemente)

```
app/
├── presentation/     ← ViewModels, UI, Compose
├── domain/           ← Entidades, UseCases, interfaces de Repository
└── data/             ← Implementaciones de Repository, DataSources, DTOs
    ├── repository/
    ├── datasource/
    ├── dto/
    └── mapper/
```

El SDK encaja **exactamente en `data/`**. No toca `domain/` ni `presentation/`.

---

## Mapa mental: dónde va cada cosa

```
┌─────────────────────────────────────────────────────────────┐
│  domain/                                                    │
│                                                             │
│  ├── model/User.kt              ← TU entidad de dominio     │
│  ├── repository/UserRepository   ← TU interfaz              │
│  └── usecase/GetUsersUseCase     ← TU use case              │
│                                                             │
│  ⚠️ AQUÍ NO IMPORTAS NADA DEL SDK                            │
│  ⚠️ domain/ no sabe que el SDK existe                        │
├─────────────────────────────────────────────────────────────┤
│  data/                                                      │
│                                                             │
│  ├── dto/UserDto.kt              ← DTO del SDK o tuyo       │
│  ├── mapper/UserMapper.kt        ← DTO → Entity de domain   │
│  ├── datasource/UserRemoteDS.kt  ← USA el SDK aquí          │
│  └── repository/UserRepoImpl.kt  ← Implementa tu interfaz   │
│                                                             │
│  ✅ AQUÍ SÍ importas el SDK                                 │
│  ✅ data/ es el ÚNICO lugar que conoce al SDK               │
├─────────────────────────────────────────────────────────────┤
│  di/                                                        │
│                                                             │
│  └── NetworkModule.kt            ← Crea el executor y lo    │
│                                     inyecta en los DS       │
└─────────────────────────────────────────────────────────────┘
```

---

## Paso a paso

### 1. Define tu entidad en `domain/` (sin tocar el SDK)

```kotlin
// domain/model/User.kt
package com.tuapp.domain.model

data class User(
    val id: Long,
    val displayName: String,
    val email: String,
    val company: String?
)
```

### 2. Define tu interfaz de repository en `domain/` (sin tocar el SDK)

```kotlin
// domain/repository/UserRepository.kt
package com.tuapp.domain.repository

import com.tuapp.domain.model.User

interface UserRepository {
    suspend fun getUsers(): Result<List<User>>
    suspend fun getUser(id: Long): Result<User>
}
```

> **Nota:** Tu `domain/` usa `Result<T>` de Kotlin estándar (o tu propio tipo). **No usa `NetworkResult<T>` del SDK.** Eso es un detalle de `data/`.

### 3. Crea tu DTO en `data/` (o reutiliza el del SDK)

```kotlin
// data/dto/UserDto.kt
package com.tuapp.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val username: String,
    val email: String,
    val phone: String? = null,
    @SerialName("company") val companyDto: CompanyDto? = null
)

@Serializable
data class CompanyDto(
    val name: String
)
```

### 4. Crea tu mapper en `data/`

```kotlin
// data/mapper/UserMapper.kt
package com.tuapp.data.mapper

import com.tuapp.data.dto.UserDto
import com.tuapp.domain.model.User

object UserMapper {

    fun toDomain(dto: UserDto): User = User(
        id = dto.id,
        displayName = dto.name,
        email = dto.email,
        company = dto.companyDto?.name
    )

    fun toDomain(dtos: List<UserDto>): List<User> = dtos.map(::toDomain)
}
```

### 5. Crea tu DataSource en `data/` — **aquí entra el SDK**

```kotlin
// data/datasource/UserRemoteDataSource.kt
package com.tuapp.data.datasource

import com.dancr.platform.network.client.HttpMethod
import com.dancr.platform.network.client.HttpRequest
import com.dancr.platform.network.datasource.RemoteDataSource
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.result.NetworkResult
import com.tuapp.data.dto.UserDto
import kotlinx.serialization.json.Json

class UserRemoteDataSource(
    executor: SafeRequestExecutor
) : RemoteDataSource(executor) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchUsers(): NetworkResult<List<UserDto>> = execute(
        request = HttpRequest(
            path = "/users",
            method = HttpMethod.GET
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )

    suspend fun fetchUser(id: Long): NetworkResult<UserDto> = execute(
        request = HttpRequest(
            path = "/users/$id",
            method = HttpMethod.GET
        ),
        deserialize = { response ->
            json.decodeFromString(response.body!!.decodeToString())
        }
    )
}
```

**Lo que hace el SDK aquí:**
- `RemoteDataSource` es la clase base del SDK que envuelve al `SafeRequestExecutor`
- `execute()` manda la request, maneja retry, clasifica errores, y te devuelve `NetworkResult<T>`
- Tú solo defines la ruta, el método HTTP, y cómo deserializar

### 6. Implementa tu Repository en `data/` — **la capa puente**

Aquí es donde conviertes `NetworkResult<T>` del SDK a `Result<T>` de tu `domain/`:

```kotlin
// data/repository/UserRepositoryImpl.kt
package com.tuapp.data.repository

import com.dancr.platform.network.result.NetworkResult
import com.dancr.platform.network.result.NetworkError
import com.tuapp.data.datasource.UserRemoteDataSource
import com.tuapp.data.mapper.UserMapper
import com.tuapp.domain.model.User
import com.tuapp.domain.repository.UserRepository

class UserRepositoryImpl(
    private val remoteDataSource: UserRemoteDataSource
) : UserRepository {

    override suspend fun getUsers(): Result<List<User>> =
        remoteDataSource.fetchUsers().toResult { UserMapper.toDomain(it) }

    override suspend fun getUser(id: Long): Result<User> =
        remoteDataSource.fetchUser(id).toResult { UserMapper.toDomain(it) }
}

// Extensión para convertir NetworkResult → Result
private fun <T, R> NetworkResult<T>.toResult(mapper: (T) -> R): Result<R> =
    when (this) {
        is NetworkResult.Success -> Result.success(mapper(data))
        is NetworkResult.Failure -> Result.failure(error.toException())
    }

// Extensión para convertir NetworkError → Exception que entiende tu domain
private fun NetworkError.toException(): Exception = when (this) {
    is NetworkError.Connectivity -> java.io.IOException(message)
    is NetworkError.Timeout      -> java.net.SocketTimeoutException(message)
    is NetworkError.Authentication -> SecurityException("Auth required")
    is NetworkError.Authorization  -> SecurityException("Access denied")
    is NetworkError.ServerError    -> RuntimeException("Server error: $statusCode")
    else -> RuntimeException(message)
}
```

**¿Por qué este paso?** Porque tu `domain/` no debe saber que existe `NetworkResult`. El Repository es el **adaptador** entre el mundo del SDK y tu dominio.

### 7. Configura el DI (Hilt)

```kotlin
// di/NetworkModule.kt
package com.tuapp.di

import com.dancr.platform.network.config.NetworkConfig
import com.dancr.platform.network.config.RetryPolicy
import com.dancr.platform.network.execution.DefaultSafeRequestExecutor
import com.dancr.platform.network.execution.SafeRequestExecutor
import com.dancr.platform.network.ktor.KtorErrorClassifier
import com.dancr.platform.network.ktor.KtorHttpEngine
import com.tuapp.data.datasource.UserRemoteDataSource
import com.tuapp.data.repository.UserRepositoryImpl
import com.tuapp.domain.repository.UserRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideNetworkConfig(): NetworkConfig = NetworkConfig(
        baseUrl = "https://api.tuempresa.com/v1",
        defaultHeaders = mapOf("Accept" to "application/json"),
        connectTimeout = 15.seconds,
        readTimeout = 30.seconds,
        retryPolicy = RetryPolicy.ExponentialBackoff(maxRetries = 2)
    )

    @Provides
    @Singleton
    fun provideExecutor(config: NetworkConfig): SafeRequestExecutor {
        val engine = KtorHttpEngine.create(config)
        return DefaultSafeRequestExecutor(
            engine = engine,
            config = config,
            classifier = KtorErrorClassifier()
        )
    }

    @Provides
    @Singleton
    fun provideUserRemoteDataSource(
        executor: SafeRequestExecutor
    ): UserRemoteDataSource = UserRemoteDataSource(executor)

    @Provides
    @Singleton
    fun provideUserRepository(
        dataSource: UserRemoteDataSource
    ): UserRepository = UserRepositoryImpl(dataSource)
}
```

### 8. Usa en tu UseCase (sin saber nada del SDK)

```kotlin
// domain/usecase/GetUsersUseCase.kt
package com.tuapp.domain.usecase

import com.tuapp.domain.model.User
import com.tuapp.domain.repository.UserRepository

class GetUsersUseCase(
    private val repository: UserRepository
) {
    suspend operator fun invoke(): Result<List<User>> =
        repository.getUsers()
}
```

### 9. Usa en tu ViewModel (sin saber nada del SDK)

```kotlin
// presentation/UserViewModel.kt
class UserViewModel @Inject constructor(
    private val getUsers: GetUsersUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserUiState>(UserUiState.Loading)
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    fun loadUsers() {
        viewModelScope.launch {
            _uiState.value = UserUiState.Loading
            getUsers()
                .onSuccess { _uiState.value = UserUiState.Success(it) }
                .onFailure { _uiState.value = UserUiState.Error(it.message ?: "Error") }
        }
    }
}
```

---

## Diagrama de dependencias

```
presentation/                  domain/                    data/
┌──────────────┐        ┌──────────────────┐       ┌─────────────────────┐
│ UserViewModel │───────▶│ GetUsersUseCase  │       │                     │
└──────────────┘        │                  │       │ UserRepositoryImpl  │
                        │ UserRepository   │◀──────│   (implementa)      │
                        │   (interfaz)     │       │                     │
                        │                  │       │ UserRemoteDataSource│
                        │ User             │       │   (usa SDK)         │
                        │   (entidad)      │       │                     │
                        └──────────────────┘       │ UserDto + Mapper    │
                                                   └─────────┬───────────┘
                         NO conoce el SDK                     │
                                                              ▼
                                                   ┌─────────────────────┐
                                                   │   Core Data Platform│
                                                   │   SDK               │
                                                   │                     │
                                                   │ SafeRequestExecutor │
                                                   │ RemoteDataSource    │
                                                   │ NetworkResult       │
                                                   │ NetworkError        │
                                                   └─────────────────────┘
```

---

## Regla de oro: quién sabe qué

| Capa | ¿Sabe del SDK? | ¿Importa tipos del SDK? | ¿Qué tipos usa? |
|---|---|---|---|
| `presentation/` | ❌ No | Nunca | `Result<T>`, tus entidades de `domain/` |
| `domain/` | ❌ No | Nunca | `Result<T>`, tus entidades, tus interfaces |
| `data/` | ✅ Sí | Solo aquí | `SafeRequestExecutor`, `RemoteDataSource`, `NetworkResult`, `NetworkError`, `HttpRequest` |
| `di/` | ✅ Sí | Solo para crear | `NetworkConfig`, `KtorHttpEngine`, `DefaultSafeRequestExecutor` |

---

## Alternativa: si quieres exponer `NetworkResult` directamente

Si no te importa que `domain/` conozca `NetworkResult` (equipos pequeños, proyectos internos), puedes simplificar:

```kotlin
// domain/repository/UserRepository.kt
import com.dancr.platform.network.result.NetworkResult

interface UserRepository {
    suspend fun getUsers(): NetworkResult<List<User>>
}
```

```kotlin
// data/repository/UserRepositoryImpl.kt
class UserRepositoryImpl(
    private val remoteDataSource: UserRemoteDataSource
) : UserRepository {

    override suspend fun getUsers(): NetworkResult<List<User>> =
        remoteDataSource.fetchUsers().map(UserMapper::toDomain)
}
```

Esto elimina la conversión `NetworkResult → Result` y te da acceso directo a `.fold()`, `.map()`, `.onFailure { error -> }` con errores tipados en el ViewModel. Es más simple pero acopla tu dominio al SDK.

**¿Cuándo vale la pena?**
- Tu app solo habla con APIs vía este SDK (no tienes otras fuentes de datos remotas)
- El equipo es pequeño y todos entienden `NetworkResult`
- No necesitas abstraer para tests con otras implementaciones

---

## Manejo de errores detallado en `data/`

Si tu proyecto usa excepciones custom para cada caso de error:

```kotlin
// domain/exception/AppExceptions.kt
package com.tuapp.domain.exception

class NoConnectionException : Exception("Sin conexión a internet")
class SessionExpiredException : Exception("Tu sesión expiró")
class ServerDownException(val code: Int) : Exception("Servidor no disponible")
class ApiContractException : Exception("Error procesando respuesta")
```

```kotlin
// data/repository/UserRepositoryImpl.kt
private fun NetworkError.toException(): Exception = when (this) {
    is NetworkError.Connectivity   -> NoConnectionException()
    is NetworkError.Timeout        -> NoConnectionException()
    is NetworkError.Authentication -> SessionExpiredException()
    is NetworkError.Authorization  -> SessionExpiredException()
    is NetworkError.ServerError    -> ServerDownException(statusCode)
    is NetworkError.Serialization  -> ApiContractException()
    else -> RuntimeException(message)
}
```

---

## Con autenticación

```kotlin
// data/auth/AppCredentialProvider.kt
package com.tuapp.data.auth

import com.dancr.platform.security.credential.Credential
import com.dancr.platform.security.credential.CredentialProvider

class AppCredentialProvider(
    private val tokenStore: TokenStore  // tu clase que guarda tokens
) : CredentialProvider {

    override suspend fun current(): Credential? {
        val token = tokenStore.getAccessToken() ?: return null
        return Credential.Bearer(token)
    }
}
```

```kotlin
// di/NetworkModule.kt — agregar al módulo
@Provides
@Singleton
fun provideExecutor(
    config: NetworkConfig,
    credentialProvider: CredentialProvider
): SafeRequestExecutor {
    val engine = KtorHttpEngine.create(config)

    val authInterceptor = RequestInterceptor { request, _ ->
        val credential = credentialProvider.current()
            ?: return@RequestInterceptor request
        val headers = CredentialHeaderMapper.toHeaders(credential)
        request.copy(headers = request.headers + headers)
    }

    return DefaultSafeRequestExecutor(
        engine = engine,
        config = config,
        classifier = KtorErrorClassifier(),
        interceptors = listOf(authInterceptor)
    )
}
```

---

## Resumen

| Lo que quieres hacer | Dónde va | Clase del SDK que usas |
|---|---|---|
| Definir entidades | `domain/model/` | Ninguna |
| Definir interfaz de repo | `domain/repository/` | Ninguna (o `NetworkResult` si simplificas) |
| Hacer la request HTTP | `data/datasource/` | `RemoteDataSource`, `HttpRequest`, `SafeRequestExecutor` |
| Convertir DTO → Entidad | `data/mapper/` | Ninguna |
| Implementar el repo | `data/repository/` | `NetworkResult`, `NetworkError` |
| Convertir `NetworkResult → Result` | `data/repository/` | `NetworkResult`, `NetworkError` |
| Crear el executor | `di/` | `NetworkConfig`, `KtorHttpEngine`, `DefaultSafeRequestExecutor` |
| Agregar auth | `di/` o `data/auth/` | `CredentialProvider`, `Credential`, `RequestInterceptor` |
