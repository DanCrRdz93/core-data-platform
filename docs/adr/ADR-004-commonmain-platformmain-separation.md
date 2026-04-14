# ADR-004: Estrategia de separación commonMain / platformMain

## Estado

**Aceptado**

## Contexto

Los proyectos Kotlin Multiplatform (KMP) deben decidir cómo distribuir el código entre source sets:

- **`commonMain`** — compartido entre todos los targets. Solo puede usar Kotlin stdlib, librerías `kotlinx`, y otros módulos KMP.
- **`androidMain`** — tiene acceso al Android SDK (`android.content.Context`, `android.security.keystore`, `androidx.security`).
- **`iosMain`** — tiene acceso a frameworks de Apple vía cinterop (`platform.Security`, `platform.Foundation`).

La estrategia de distribución impacta directamente:
- Cuánto código se comparte vs. se duplica.
- Qué tan testeable es la lógica compartida.
- Qué tan fácil es agregar un nuevo target de plataforma (ej. JVM backend, watchOS).

## Decisión

**Maximizar `commonMain`. Empujar el código específico de plataforma a los bordes absolutos.**

La regla es:

| Tipo de código | Source set | Ejemplos |
|---|---|---|
| Interfaces y contratos | `commonMain` | `HttpEngine`, `SecretStore`, `SessionController`, `TrustPolicy` |
| Sealed classes y modelos de datos | `commonMain` | `NetworkError`, `Credential`, `SessionState`, `RetryPolicy` |
| Implementaciones por defecto (lógica pura) | `commonMain` | `DefaultSafeRequestExecutor`, `DefaultErrorClassifier`, `DefaultLogSanitizer` |
| Pipeline de ejecución y lógica de reintentos | `commonMain` | `DefaultSafeRequestExecutor` |
| Clases de configuración | `commonMain` | `NetworkConfig`, `SecurityConfig`, `AndroidStoreConfig`, `KeychainConfig` |
| I/O de plataforma (storage, keychain) | `androidMain` / `iosMain` | `AndroidSecretStore`, `IosSecretStore` |

**Distribución actual:**

| Módulo | `commonMain` | `androidMain` | `iosMain` |
|---|---|---|---|
| `:network-core` | 20 archivos | 0 archivos | 0 archivos |
| `:network-ktor` | 2 archivos | 0 archivos | 0 archivos |
| `:security-core` | 15 archivos | 2 archivos | 2 archivos |
| `:sample-api` | 6 archivos | 0 archivos | 0 archivos |
| **Total** | **43 archivos** | **2 archivos** | **2 archivos** |

95%+ del código está en `commonMain`.

### Reglas de source sets de plataforma

1. **Solo implementar interfaces definidas en `commonMain`.** El código de plataforma nunca define nuevos contratos públicos.
2. **Las data classes de configuración están en `commonMain`**, incluso si referencian conceptos de plataforma. `AndroidStoreConfig` (nombre de preferences, alias de master key) es una data class simple sin imports de Android. `KeychainConfig` (nombre de servicio, nivel de accesibilidad) es una data class simple sin imports de iOS.
3. **Los enums de plataforma están en source sets de plataforma** solo cuando mapean a constantes de plataforma. `KeychainAccessibility` está en `iosMain` porque sus valores corresponden a constantes `kSecAttrAccessible*`.
4. **La selección de engine de Ktor usa Gradle, no source sets.** `:network-ktor` no tiene código Kotlin en `androidMain` ni `iosMain`. La selección de engine de plataforma (`ktor-client-okhttp` vs. `ktor-client-darwin`) se declara en las dependencias de `build.gradle.kts`.

## Consecuencias

### Positivas

- **Máximo código compartido.** Lógica de negocio, clasificación de errores, políticas de reintento, cadenas de interceptors, tipos de resultado — todo compartido. Sin reimplementación por plataforma.
- **Superficie de testing única.** Los tests para `DefaultSafeRequestExecutor`, `DefaultErrorClassifier`, `NetworkResult`, y toda la lógica pura corren en `commonTest` — una vez, para todas las plataformas.
- **Fácil agregar targets.** Agregar JVM (para backend) o watchOS requeriría solo nuevas implementaciones de `SecretStore` en sus respectivos source sets. Todos los contratos y lógica de pipeline vienen gratis.
- **Límite claro.** Cuando un desarrollador pregunta *"¿esto debería ir en commonMain?"*, la respuesta es casi siempre sí. La única excepción son llamadas directas a APIs de plataforma.

### Negativas

- **Las clases de configuración en `commonMain` pueden ser confusas.** `AndroidStoreConfig` es una data class de `commonMain` que solo tiene sentido en Android. Compila en iOS pero no tiene uso ahí. Esto se acepta porque mantiene el tipo visible para documentación y firmas de factory.
- **Sin uso de `expect`/`actual`.** El proyecto actualmente usa interfaces + implementaciones de plataforma en vez de declaraciones `expect`/`actual`. Esto es intencional — `expect`/`actual` crea acoplamiento de compilación entre source sets, mientras que las interfaces permiten inyección de implementación y testing más fácil. `expect`/`actual` puede introducirse después para primitivas de plataforma verdaderas (ej. `Dispatchers.IO` si se necesita en `commonMain`).
- **Los source sets de plataforma son delgados.** `androidMain` e `iosMain` tienen 2 archivos cada uno. Esta asimetría es por diseño pero puede sorprender a desarrolladores que esperan más código de plataforma en un proyecto KMP.
