# :security-core

**Abstracciones de Seguridad para Kotlin Multiplatform**

Este módulo define la superficie completa de contratos para gestión de credenciales, ciclo de vida de sesiones, almacenamiento seguro, evaluación de confianza TLS y sanitización de logs — todo como abstracciones agnósticas de plataforma con implementaciones específicas de plataforma en los bordes.

---

## Propósito

`:security-core` responde una pregunta:

> *"¿Cómo gestiono credenciales, sesiones, secretos y políticas de confianza a través de Android e iOS — sin acoplar ninguna lógica de negocio a APIs de seguridad específicas de plataforma?"*

Este módulo tiene **cero dependencia en `:network-core`**. No sabe nada de HTTP, Ktor ni ejecución de requests. El punto de integración entre networking y seguridad es `CredentialHeaderMapper`, que convierte un `Credential` en un simple `Map<String, String>` — sin tipos de red involucrados.

---

## Responsabilidades

| Responsabilidad | Dueño |
|---|---|
| Modelar credenciales de autenticación | `Credential` (sealed interface) |
| Proveer la credencial activa para requests | `CredentialProvider` |
| Convertir credenciales a headers HTTP seguros | `CredentialHeaderMapper` |
| Gestionar el ciclo de vida de sesión reactivamente | `SessionController`, `SessionState`, `SessionEvent` |
| Almacenar secretos de forma segura por plataforma | `SecretStore`, `AndroidSecretStore`, `IosSecretStore` |
| Evaluar confianza de host y certificate pinning | `TrustPolicy`, `TrustEvaluation`, `CertificatePin` |
| Redactar datos sensibles en logs | `LogSanitizer`, `DefaultLogSanitizer` |
| Configurar políticas de seguridad | `SecurityConfig` |
| Modelar errores de seguridad semánticamente | `SecurityError`, `Diagnostic` |
| Proveer codificación Base64 cross-platform | Utilidad `Base64` |

---

## Contratos Principales

### Credential

```kotlin
sealed interface Credential {
    data class Bearer(val token: String)
    data class ApiKey(val key: String, val headerName: String = "X-API-Key")
    data class Basic(val username: String, val password: String)
    data class Custom(val type: String, val properties: Map<String, String>)
}
```

Sealed interface exhaustiva — todos los tipos de credenciales son conocidos en tiempo de compilación. `Custom` es la vía de escape para esquemas de auth propietarios.

### CredentialProvider

```kotlin
interface CredentialProvider {
    suspend fun current(): Credential?
}
```

Retorna la credencial activa actualmente, o `null` si no está autenticado. Las implementaciones deben leer de `SessionController` o `SecretStore` — nunca cachear tokens obsoletos.

> **Estado:** Interfaz definida. Implementación concreta pendiente (depende de las implementaciones de `SessionController` y `SecretStore`).

### CredentialHeaderMapper

```kotlin
object CredentialHeaderMapper {
    fun toHeaders(credential: Credential): Map<String, String>
}
```

Conversión pura y sin estado — sin dependencia de network-core:

| Credential Type | Output |
|---|---|
| `Bearer("abc123")` | `{"Authorization": "Bearer abc123"}` |
| `ApiKey("key", "X-API-Key")` | `{"X-API-Key": "key"}` |
| `Basic("user", "pass")` | `{"Authorization": "Basic dXNlcjpwYXNz"}` |
| `Custom("OAuth2", props)` | `props` as-is |

Usa `Base64.encodeToString()` del paquete `util/` para codificación de auth Basic.

### SessionController

```kotlin
interface SessionController {
    val state: StateFlow<SessionState>       // Reactive session state
    val events: Flow<SessionEvent>           // Lifecycle events stream
    suspend fun startSession(credentials: SessionCredentials)
    suspend fun refreshSession(): Boolean
    suspend fun endSession()
}
```

`SessionState` es una sealed interface:

```kotlin
sealed interface SessionState {
    data object Idle : SessionState               // No active session
    data class Active(val credential: Credential) // Authenticated
    data object Expired : SessionState            // Session expired, refresh needed
}
```

`SessionEvent` captura transiciones del ciclo de vida:

```kotlin
sealed class SessionEvent {
    data object Started
    data object Refreshed
    data object Expired
    data object Ended
    data class RefreshFailed(val error: SecurityError)
}
```

> **Estado:** Interfaz definida con `StateFlow` para observación reactiva de UI. Implementación pendiente.

### SecretStore

```kotlin
interface SecretStore {
    suspend fun putString(key: String, value: String)
    suspend fun getString(key: String): String?
    suspend fun putBytes(key: String, value: ByteArray)
    suspend fun getBytes(key: String): ByteArray?
    suspend fun remove(key: String)
    suspend fun clear()
    suspend fun contains(key: String): Boolean
}
```

Todas las operaciones son `suspend` — las APIs de almacenamiento de plataforma pueden involucrar I/O.

### TrustPolicy

```kotlin
interface TrustPolicy {
    fun evaluateHost(hostname: String): TrustEvaluation
    fun pinnedCertificates(): Map<String, Set<CertificatePin>>
}
```

```kotlin
sealed interface TrustEvaluation {
    data object Trusted
    data class Denied(val reason: String)
}

data class CertificatePin(val algorithm: String, val hash: String)
```

`DefaultTrustPolicy` confía en todos los hosts (default de desarrollo). Las apps de producción sobreescriben con conjuntos de pines por hostname.

### LogSanitizer

```kotlin
interface LogSanitizer {
    fun sanitize(key: String, value: String): String
}
```

`DefaultLogSanitizer` usa `SecurityConfig` para identificar claves sensibles y reemplaza sus valores con `"██"`.

Funciones de extensión para sanitización en lote:

```kotlin
fun LogSanitizer.sanitizeHeaders(headers: Map<String, String>): Map<String, String>
fun LogSanitizer.sanitizeMultiValueHeaders(headers: Map<String, List<String>>): Map<String, List<String>>
```

### SecurityError

```kotlin
sealed class SecurityError {
    abstract val message: String           // Safe for end users
    abstract val diagnostic: Diagnostic?   // Internal debugging only

    class TokenExpired
    class TokenRefreshFailed
    class InvalidCredentials
    class SecureStorageFailure
    class CertificatePinningFailure(val host: String)
    class Unknown
}
```

---

## Estructura Interna

```
security-core/src/
├── commonMain/kotlin/com/dancr/platform/security/
│   ├── config/
│   │   └── SecurityConfig.kt              # Headers/claves sensibles, placeholder de redacción
│   ├── credential/
│   │   ├── Credential.kt                  # Sealed interface: Bearer, ApiKey, Basic, Custom
│   │   ├── CredentialProvider.kt          # Interfaz — provee credencial activa
│   │   └── CredentialHeaderMapper.kt      # Credential → Map<String, String>
│   ├── error/
│   │   ├── SecurityError.kt               # Taxonomía de errores semánticos (sealed class)
│   │   └── Diagnostic.kt                  # Detalles internos de error
│   ├── sanitizer/
│   │   ├── LogSanitizer.kt                # Interfaz + funciones de extensión
│   │   └── DefaultLogSanitizer.kt         # Redacción de claves basada en configuración
│   ├── session/
│   │   ├── SessionController.kt           # Contrato de ciclo de vida de sesión (basado en StateFlow)
│   │   ├── SessionState.kt                # Idle | Active(credential) | Expired
│   │   ├── SessionCredentials.kt          # Credential + refreshToken + expiresAtMs
│   │   └── SessionEvent.kt                # Started, Refreshed, Expired, Ended, RefreshFailed
│   ├── store/
│   │   └── SecretStore.kt                 # Interfaz de almacenamiento seguro clave-valor
│   ├── trust/
│   │   ├── TrustPolicy.kt                # Interfaz de evaluación de host + cert pinning
│   │   ├── TrustEvaluation.kt            # Trusted | Denied(reason)
│   │   ├── CertificatePin.kt             # Par algoritmo + hash
│   │   └── DefaultTrustPolicy.kt         # Default que confía en todo
│   └── util/
│       └── Base64.kt                      # Codificación Base64 cross-platform
│
├── androidMain/kotlin/com/dancr/platform/security/store/
│   ├── AndroidSecretStore.kt              # Impl de SecretStore (skeleton)
│   └── AndroidStoreConfig.kt             # Nombre de preferences, alias de master key, prefijo de clave
│
└── iosMain/kotlin/com/dancr/platform/security/store/
    ├── IosSecretStore.kt                  # Impl de SecretStore (skeleton)
    └── KeychainConfig.kt                  # Nombre de servicio, grupo de acceso, nivel de accesibilidad
```

---

## Arquitectura

### Separación por Preocupación

```mermaid
graph TD
    subgraph "Credential Management"
        CR[Credential]
        CP[CredentialProvider]
        CHM[CredentialHeaderMapper]
    end

    subgraph "Session Lifecycle"
        SC[SessionController]
        SS[SessionState]
        SE[SessionEvent]
        SCR[SessionCredentials]
    end

    subgraph "Secure Storage"
        STO[SecretStore]
        AS[AndroidSecretStore]
        IS[IosSecretStore]
    end

    subgraph "Trust & TLS"
        TP[TrustPolicy]
        TE[TrustEvaluation]
        PIN[CertificatePin]
    end

    subgraph "Log Protection"
        LS[LogSanitizer]
        DLS[DefaultLogSanitizer]
        CFG[SecurityConfig]
    end

    SC --> SS
    SC --> SE
    SC --> SCR
    SCR --> CR
    SS --> CR
    CP --> CR
    CHM --> CR
    DLS --> CFG
    AS -->|implements| STO
    IS -->|implements| STO

    style CR fill:#e1f5fe,stroke:#0277bd
    style STO fill:#e8f5e9,stroke:#2e7d32
    style SC fill:#fff3e0,stroke:#ef6c00
    style TP fill:#fce4ec,stroke:#c62828
    style LS fill:#f3e5f5,stroke:#7b1fa2
```

### Distribución de Source Sets de Plataforma

```mermaid
graph LR
    subgraph commonMain["commonMain (all contracts)"]
        A[SecretStore interface]
        B[Credential / CredentialProvider]
        C[SessionController]
        D[TrustPolicy / CertificatePin]
        E[LogSanitizer / SecurityConfig]
        F[SecurityError / Diagnostic]
        G[Base64 utility]
    end

    subgraph androidMain
        H["AndroidSecretStore<br/>(EncryptedSharedPreferences)"]
        I[AndroidStoreConfig]
    end

    subgraph iosMain
        J["IosSecretStore<br/>(Keychain Services)"]
        K["KeychainConfig<br/>+ KeychainAccessibility"]
    end

    A --> H
    A --> J

    style commonMain fill:#e3f2fd,stroke:#1565c0
    style androidMain fill:#e8f5e9,stroke:#2e7d32
    style iosMain fill:#fff3e0,stroke:#ef6c00
```

---

## Ejemplos de Uso

### Usando CredentialHeaderMapper (sin dependencia de red)

```kotlin
val credential = Credential.Bearer("eyJhbGciOiJIUzI1NiIs...")
val headers = CredentialHeaderMapper.toHeaders(credential)
// {"Authorization": "Bearer eyJhbGciOiJIUzI1NiIs..."}
```

### Construir un interceptor de auth (en módulo consumidor)

```kotlin
// Este código vive en el módulo de dominio, NO en security-core
val authInterceptor = RequestInterceptor { request, _ ->
    val credential = credentialProvider.current()
        ?: return@RequestInterceptor request
    val headers = CredentialHeaderMapper.toHeaders(credential)
    request.copy(headers = request.headers + headers)
}
```

### Observar estado de sesión (en capa de UI)

```kotlin
// En un ViewModel
sessionController.state.collect { state ->
    when (state) {
        is SessionState.Idle -> showLoginScreen()
        is SessionState.Active -> showHomeScreen()
        is SessionState.Expired -> showSessionExpiredDialog()
    }
}
```

### Sanitizar logs

```kotlin
val sanitizer = DefaultLogSanitizer()
val rawHeaders = mapOf("Authorization" to "Bearer secret123", "Accept" to "application/json")
val safe = sanitizer.sanitizeHeaders(rawHeaders)
// {"Authorization": "██", "Accept": "application/json"}
```

### Configurar claves sensibles

```kotlin
val config = SecurityConfig(
    sensitiveHeaders = SecurityConfig.DEFAULT_SENSITIVE_HEADERS + setOf("x-custom-secret"),
    sensitiveKeys = SecurityConfig.DEFAULT_SENSITIVE_KEYS + setOf("pin_code"),
    redactedPlaceholder = "[REDACTED]"
)
val sanitizer = DefaultLogSanitizer(config)
```

### Evaluación de confianza

```kotlin
class ProductionTrustPolicy : TrustPolicy {
    override fun evaluateHost(hostname: String): TrustEvaluation {
        val allowed = setOf("api.mycompany.com", "auth.mycompany.com")
        return if (hostname in allowed) TrustEvaluation.Trusted
               else TrustEvaluation.Denied("Host $hostname is not in the allowed list")
    }

    override fun pinnedCertificates() = mapOf(
        "api.mycompany.com" to setOf(
            CertificatePin("sha256", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        )
    )
}
```

---

## Decisiones de Diseño

| Decisión | Razón |
|---|---|
| **Cero dependencia en `:network-core`** | Las preocupaciones de seguridad (credenciales, almacenamiento, confianza) son fundamentalmente independientes del transporte HTTP. Un módulo que solo necesita almacenamiento seguro no debería traer todo el stack de networking. |
| **`CredentialHeaderMapper` retorna `Map<String, String>`** | El mapper convierte credenciales a pares de headers simples sin importar `HttpRequest`, `RequestInterceptor`, ni ningún tipo de red. Esto mantiene el límite limpio. |
| **`SessionController.state` es `StateFlow`** | Habilita observación reactiva de UI. Una propiedad `val state: SessionState` requeriría polling. `StateFlow` da a los suscriptores acceso inmediato al valor actual y actualizaciones reactivas. |
| **`Credential` es una sealed interface** | Matching exhaustivo en tiempo de compilación vía `when`. Todos los tipos de credenciales son conocidos, lo que previene sorpresas en runtime. `Custom` es la vía de escape para esquemas propietarios. |
| **Las implementaciones de plataforma son skeletons con TODOs** | La interfaz y arquitectura están probadas. Las implementaciones de plataforma requieren integración cuidadosa con Android Keystore e iOS Keychain APIs, que son código de alto riesgo que debe implementarse con infraestructura de testing apropiada. |
| **`SecurityError` refleja la estructura de `NetworkError`** | Ambos usan sealed classes con `message` (seguro para usuario) + `diagnostic` (interno). Esta estructura paralela simplifica el manejo de errores en consumidores que puentean ambos módulos. |
| **`Base64` es una implementación manual** | La stdlib común de Kotlin no provee codificación Base64. La implementación manual evita una dependencia en `kotlinx-io` o APIs específicas de plataforma para una utilidad trivial. |
| **`DefaultLogSanitizer` usa matching por clave, no por patrón** | Simple, predecible y rápido. Si una clave está en el set de sensibles, su valor se redacta completamente. Sin regex, sin redacción parcial, sin falsos negativos. |

---

## Detalles de Implementación de Plataforma

### Android: `AndroidSecretStore`

| Aspecto | Detalle |
|---|---|
| **Backend** | `EncryptedSharedPreferences` + Android Keystore |
| **Encriptación** | AES-256-GCM para valores, AES-256-SIV para claves |
| **Gestión de claves** | `MasterKey` con backing StrongBox configurable |
| **Threading** | Todas las operaciones despachadas a `Dispatchers.IO` |
| **Configuración** | `AndroidStoreConfig` — nombre de preferences, alias de master key, prefijo de clave, flag de StrongBox |
| **Estado** | Skeleton con TODOs paso a paso. Requiere `androidx.security:security-crypto:1.1.0-alpha06`. |

### iOS: `IosSecretStore`

| Aspecto | Detalle |
|---|---|
| **Backend** | Keychain Services (`kSecClassGenericPassword`) |
| **APIs** | `SecItemAdd`, `SecItemCopyMatching`, `SecItemUpdate`, `SecItemDelete` |
| **Configuración** | `KeychainConfig` — nombre de servicio, grupo de acceso, nivel de accesibilidad |
| **Niveles de accesibilidad** | `WHEN_UNLOCKED`, `AFTER_FIRST_UNLOCK`, `WHEN_PASSCODE_SET_THIS_DEVICE_ONLY`, variantes device-only |
| **Threading** | Todas las operaciones despachadas a `Dispatchers.IO` |
| **Estado** | Skeleton con TODOs paso a paso. Usa framework `platform.Security` vía cinterop (incorporado). |

---

## Extensibilidad

| Punto de Extensión | Cómo |
|---|---|
| **Nuevo tipo de credencial** | Agregar a la sealed interface `Credential` + actualizar `CredentialHeaderMapper.toHeaders()` |
| **Proveedor de credenciales personalizado** | Implementar `CredentialProvider` respaldado por tu sistema de auth |
| **Almacén de secretos personalizado** | Implementar `SecretStore` para un backend diferente (ej. SQLCipher, en memoria para testing) |
| **Política de confianza personalizada** | Implementar `TrustPolicy` con tus conjuntos de pines y reglas de host |
| **Sanitización de logs personalizada** | Implementar `LogSanitizer` con redacción basada en patrones o consciente del contexto |
| **Nuevos eventos de sesión** | Agregar a la sealed class `SessionEvent` (requiere modificar el archivo) |
| **Nuevos errores de seguridad** | Agregar a la sealed class `SecurityError` |

---

## Limitaciones Actuales

| Limitación | Contexto |
|---|---|
| **Las implementaciones de `SecretStore` de plataforma son skeletons** | `AndroidSecretStore` e `IosSecretStore` tienen cuerpos TODO con guía de implementación detallada, pero aún no compilan a código funcional. |
| **Sin implementación de `SessionController`** | La interfaz y el modelo de estado están definidos, pero no existe `DefaultSessionController`. Esto requiere implementaciones funcionales de `SecretStore` primero. |
| **`Diagnostic` está duplicado con `:network-core`** | Ambos módulos definen data classes `Diagnostic` idénticas. Un futuro módulo `:platform-common` debería unificarlas. |
| **Sin autenticación biométrica** | `SecretStore` no soporta acceso restringido por biometría (ej. `setUserAuthenticationRequired` en Android, `kSecAccessControlBiometryAny` en iOS). |
| **`CredentialProvider` carece de hooks de refresh/invalidate** | La interfaz actual solo tiene `current()`. El refresh e invalidación de tokens requieren futuros métodos `refresh()` e `invalidate()`. |

---

## TODOs y Trabajo Futuro

| Ítem | Ubicación | Descripción |
|---|---|---|
| Completar `AndroidSecretStore` | `androidMain/store/` | Conectar EncryptedSharedPreferences con MasterKey + mapeo de errores |
| Completar `IosSecretStore` | `iosMain/store/` | Conectar Keychain Services con manejo apropiado de errores OSStatus |
| `DefaultSessionController` | `session/` | Implementación basada en StateFlow con almacenamiento de tokens y lógica de refresh |
| Impl de `CredentialProvider` | `credential/` | Respaldado por `SessionController` + `SecretStore` |
| `refresh()` en `CredentialProvider` | `credential/` | Refresh proactivo de token antes de expiración |
| `invalidate()` en `CredentialProvider` | `credential/` | Limpiar credencial cacheada en 401 |
| `invalidate()` en `SessionController` | `session/` | Forzar logout desde cualquier capa |
| Resultado sealed `RefreshOutcome` | `session/` | Reemplazar retorno `Boolean` de `refreshSession()` con resultado semántico |
| Conveniencia `isAuthenticated` | `SessionController` | Derivado de `state` para verificaciones simples |
| `keys()` en `SecretStore` | `store/` | Enumerar claves almacenadas para migración/diagnósticos |
| `putStringIfAbsent()` en `SecretStore` | `store/` | Escritura atómica si-ausente para inicialización libre de race conditions |
| Integración biométrica | `store/` | Restricción biométrica específica de plataforma para acceso a secretos |
| Unificar `Diagnostic` | cross-module | Extraer a módulo compartido `:platform-common` |

---

## Dependencias

```toml
# Única dependencia — sin networking, sin serialización
[dependencies]
kotlinx-coroutines-core = "1.10.1"
```

Este módulo compila para **todos los targets**: Android, iosX64, iosArm64, iosSimulatorArm64.
