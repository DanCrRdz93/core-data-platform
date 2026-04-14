# ADR-006: Clasificación centralizada de errores

## Estado

**Aceptado**

## Contexto

Las operaciones HTTP pueden fallar de muchas formas: fallos de transporte (resolución DNS, timeout TCP, handshake TLS), respuestas de error HTTP (401, 403, 404, 500), excepciones de deserialización, y errores de runtime inesperados. Cada tipo de fallo requiere diferente manejo por los consumidores:

- **Connectivity** → mostrar banner "sin internet", encolar para reintento offline.
- **Timeout** → reintentar con backoff.
- **401 Authentication** → redirigir a login o disparar refresh de token.
- **403 Authorization** → mostrar mensaje "acceso denegado".
- **500 Server Error** → reintentar automáticamente, mostrar error genérico.
- **Serialization** → loguear diagnostic, mostrar error genérico (desajuste de contrato API).

Sin clasificación centralizada:

- Cada data source interpreta códigos de estado HTTP crudos de forma diferente.
- Excepciones específicas de plataforma (`java.net.SocketTimeoutException`, `NSURLErrorTimedOut`) se filtran a la lógica de negocio.
- Agregar una nueva categoría de error (ej. rate limiting en 429) requiere cambios en cada data source.
- Las decisiones de reintento están dispersas y son inconsistentes.

## Decisión

Todos los errores se clasifican en un **punto único** — la interfaz `ErrorClassifier` — y se expresan como subtipos de la sealed class `NetworkError`. Ninguna excepción cruda o código de estado HTTP escapa del pipeline de ejecución.

### Arquitectura de clasificación

```
Throwable / RawResponse
        │
        ▼
  ErrorClassifier.classify(response?, cause?)
        │
        ├── cause != null → classifyThrowable(cause)
        │     │
        │     ├── Class name contains "Timeout"      → NetworkError.Timeout
        │     ├── Class name contains "UnknownHost"   → NetworkError.Connectivity
        │     ├── Class name contains "ConnectException" → NetworkError.Connectivity
        │     ├── Class name contains "Serializ"      → NetworkError.Serialization
        │     └── else                                → NetworkError.Unknown
        │
        └── response != null → classifyResponse(response)
              │
              ├── 401 → NetworkError.Authentication
              ├── 403 → NetworkError.Authorization
              ├── 4xx → NetworkError.ClientError(statusCode)
              ├── 5xx → NetworkError.ServerError(statusCode)
              └── else → NetworkError.Unknown
```

### Diseño de clasificador de dos capas

1. **`DefaultErrorClassifier`** (en `:network-core`, `open class`) — Usa heurísticas cross-platform. En `commonMain`, los tipos de excepción de plataforma no están disponibles, así que la clasificación usa pattern matching con `cause::class.simpleName`. Es un default razonable que funciona para la mayoría de excepciones.

2. **`KtorErrorClassifier`** (en `:network-ktor`, extiende `DefaultErrorClassifier`) — Sobreescribe `classifyThrowable()` para agregar matching **type-safe** para excepciones de Ktor (ej. `HttpRequestTimeoutException`). Hace fallthrough al padre para todas las excepciones no-Ktor.

Este patrón está diseñado para extensión:

```kotlin
// Futuro: clasificador consciente de plataforma
class AndroidErrorClassifier : DefaultErrorClassifier() {
    override fun classifyThrowable(cause: Throwable) = when (cause) {
        is java.net.SocketTimeoutException -> NetworkError.Timeout(...)
        is java.net.UnknownHostException -> NetworkError.Connectivity(...)
        is javax.net.ssl.SSLHandshakeException -> NetworkError.Unknown(...)  // or a future TLS error
        else -> super.classifyThrowable(cause)
    }
}
```

### La reintentabilidad es una propiedad del error

Cada subtipo de `NetworkError` declara `open val isRetryable`:

| Error | `isRetryable` |
|---|---|
| `Connectivity` | `true` |
| `Timeout` | `true` |
| `ServerError` | `true` |
| `Cancelled` | `false` |
| `Authentication` | `false` |
| `Authorization` | `false` |
| `ClientError` | `false` |
| `Serialization` | `false` |
| `ResponseValidation` | `false` |
| `Unknown` | `false` |

El executor lee `error.isRetryable` — no hardcodea qué tipos de error reintentar. Esto significa que un `ErrorClassifier` custom puede retornar errores con características de reintentabilidad diferentes sin modificar el executor.

### Modelo de error de dos audiencias

Cada `NetworkError` lleva:

- **`message: String`** — Legible por humanos, seguro para usuarios finales. Nunca contiene stack traces, códigos de estado ni jerga técnica. Ejemplo: *"Unable to reach the server"*.
- **`diagnostic: Diagnostic?`** — Datos de debugging interno. Contiene `description` (detalle técnico), `cause` (`Throwable` original), y `metadata` (pares clave-valor como `statusCode`). Nunca se muestra al usuario.

## Consecuencias

### Positivas

- **Manejo exhaustivo.** `NetworkError` es sealed. Los consumidores usan `when (error)` y el compilador verifica todas las ramas. Agregar un nuevo tipo de error es un cambio breaking en tiempo de compilación — sin omisiones silenciosas.
- **Comportamiento de reintento consistente.** La reintentabilidad se declara en el error, no la decide cada data source. El pipeline la aplica uniformemente.
- **Código consumidor limpio.** Los consumidores manejan `NetworkError.Authentication` — no `response.statusCode == 401`. El modelo de error codifica la semántica HTTP una vez, en tiempo de clasificación.
- **Agnóstico de transporte.** Los consumidores nunca ven `HttpRequestTimeoutException` o `SocketTimeoutException`. Ven `NetworkError.Timeout`. Cambiar de Ktor a OkHttp cambia qué clasificador corre, pero los tipos de error que manejan los consumidores permanecen idénticos.
- **Extensible.** Agregar 429 Rate Limiting requiere: (1) agregar `NetworkError.RateLimited` a la sealed class, (2) manejar 429 en `classifyResponse()`. Todos los consumidores obtienen un error de compilación hasta que manejen el nuevo caso.

### Negativas

- **Clasificación heurística en `commonMain`.** El matching por nombre de clase (`simpleName.contains("Timeout")`) es frágil. Una excepción llamada `CustomTimeoutHandler` sería clasificada incorrectamente. Se mitiga con clasificadores de plataforma (`KtorErrorClassifier`) que hacen matching type-safe y solo hacen fallthrough a heurísticas para excepciones desconocidas.
- **Se requiere modificación de la sealed class para nuevos errores.** Agregar `NetworkError.RateLimited` requiere editar `NetworkError.kt` y publicar una nueva versión de `:network-core`. Esto es intencional — asegura seguridad en tiempo de compilación — pero significa que la evolución de errores es un cambio coordinado del SDK, no una decisión local del consumidor.
- **Sin composición de errores.** Un único `NetworkError` no puede representar múltiples problemas simultáneos. Esto no ha sido necesario en la práctica, pero escenarios complejos (ej. "timeout durante reintento después de fallo de autenticación") solo muestran el error final.
