# ADR-002: Contratos primero, implementación después

## Estado

**Aceptado**

## Contexto

Al construir un SDK compartido consumido por múltiples aplicaciones, la estabilidad del API público es crítica. Si los consumidores dependen de clases concretas, cualquier refactorización interna (cambiar una librería HTTP, reestructurar lógica de reintentos, modificar backends de almacenamiento) arriesga romper todas las apps downstream.

Se consideraron dos enfoques:

1. **Implementación primero:** Construir clases concretas (ej. `KtorRequestExecutor`) y extraer interfaces después si se necesitan.
2. **Contratos primero:** Definir interfaces y tipos sealed primero. Construir implementaciones detrás de ellos. Los consumidores programan contra el contrato, nunca contra la clase concreta.

## Decisión

Todos los componentes principales se definen como **interfaces, sealed classes o clases abstractas** antes de escribir cualquier implementación. Los consumidores dependen exclusivamente de estos contratos.

Las implementaciones concretas:
- Se nombran con prefijo `Default` (`DefaultSafeRequestExecutor`, `DefaultErrorClassifier`, `DefaultResponseValidator`, `DefaultLogSanitizer`, `DefaultTrustPolicy`) para señalar que son defaults reemplazables.
- Se declaran como `open class` donde se espera extensión (ej. `DefaultErrorClassifier`), habilitando subclases específicas de plataforma sin reimplementar el contrato completo.
- Se inyectan vía parámetros del constructor, nunca se instancian internamente por otros componentes.

### Inventario de contratos

| Contrato (interface / sealed / abstract) | Implementación por defecto | Módulo |
|---|---|---|
| `HttpEngine` | `KtorHttpEngine` | `network-core` / `network-ktor` |
| `SafeRequestExecutor` | `DefaultSafeRequestExecutor` | `network-core` |
| `ErrorClassifier` | `DefaultErrorClassifier` (open) | `network-core` |
| `ResponseValidator` | `DefaultResponseValidator` | `network-core` |
| `RequestInterceptor` | *(consumer-provided)* | `network-core` |
| `ResponseInterceptor` | *(consumer-provided)* | `network-core` |
| `NetworkEventObserver` | `NetworkEventObserver.NOOP` | `network-core` |
| `RemoteDataSource` (abstract class) | *(consumer extends)* | `network-core` |
| `NetworkResult<T>` (sealed class) | — | `network-core` |
| `NetworkError` (sealed class) | — | `network-core` |
| `RetryPolicy` (sealed class) | — | `network-core` |
| `SecretStore` | `AndroidSecretStore`, `IosSecretStore` | `security-core` |
| `CredentialProvider` | *(consumer-provided)* | `security-core` |
| `SessionController` | *(pending)* | `security-core` |
| `TrustPolicy` | `DefaultTrustPolicy` (open) | `security-core` |
| `LogSanitizer` | `DefaultLogSanitizer` | `security-core` |
| `Credential` (sealed interface) | — | `security-core` |
| `SecurityError` (sealed class) | — | `security-core` |

## Consecuencias

### Positivas

- **API público estable.** Los consumidores programan contra `SafeRequestExecutor`, no `DefaultSafeRequestExecutor`. Refactorización interna (ej. cambiar el algoritmo de reintentos) no rompe ningún consumidor.
- **Testabilidad.** Cada dependencia es mockeable. Testear un `UserRemoteDataSource` requiere solo un mock de `SafeRequestExecutor` — sin servidor HTTP, sin Ktor, sin complejidad de coroutines.
- **Reemplazabilidad.** Reemplazar Ktor con OkHttp significa implementar `HttpEngine` en un nuevo módulo. Cero cambios en `network-core`, `security-core`, o cualquier módulo de dominio.
- **Implementación gradual.** Los contratos pueden diseñarse y revisarse antes de escribir cualquier implementación. `SessionController` es una interfaz definida hoy; su implementación puede seguir cuando `SecretStore` esté listo.

### Negativas

- **Más archivos.** Cada componente principal tiene al menos dos archivos: el contrato y la implementación por defecto. Esto se acepta como el costo de la mantenibilidad a escala.
- **Indirección.** Los desarrolladores deben navegar de la interfaz a la implementación al depurar. Se mitiga con el naming consistente `Default*` y estructura de paquetes clara.
- **Los tipos sealed requieren modificación del fuente para extenderse.** Agregar un nuevo subtipo de `NetworkError` o variante de `RetryPolicy` requiere modificar el archivo de la sealed class. Esto es intencional — fuerza a todos los consumidores a manejar nuevos casos en tiempo de compilación.
