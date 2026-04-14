# ADR-005: Pipeline de ejecución segura centralizado

## Estado

**Aceptado**

## Contexto

En un SDK multi-app, cada request de red debe pasar por un pipeline consistente que asegure:

- Se aplican headers por defecto.
- Se inyectan credenciales de autenticación.
- Se respetan las políticas de reintento.
- Se validan las respuestas antes de la deserialización.
- Se clasifican los errores en tipos semánticos.
- Se notifica a los observers para métricas y trazabilidad.
- Se propaga correctamente la cancelación de coroutines.
- Las excepciones nunca escapan sin clasificar.

Sin centralización, cada data source o repository reimplementaría partes de esta lógica, llevando a:

- Manejo de errores inconsistente entre endpoints.
- Lógica de reintentos duplicada con diferencias sutiles de comportamiento.
- Headers de auth aplicados en algunas requests pero olvidados en otras.
- Ningún punto único para agregar observabilidad.
- Testing que requiere verificar el mismo pipeline en cada data source.

Se consideraron dos enfoques:

1. **Descentralizado.** Cada `RemoteDataSource` maneja su propio reintento, validación y mapeo de errores.
2. **Centralizado.** Un único `SafeRequestExecutor` orquesta el ciclo de vida completo. Los data sources solo proveen el `HttpRequest` y una función `deserialize`.

## Decisión

Todas las operaciones de red fluyen a través de `DefaultSafeRequestExecutor`, que implementa la interfaz `SafeRequestExecutor`. Los data sources extienden `RemoteDataSource`, que delega al executor. Ningún data source llama a `HttpEngine.execute()` directamente.

### Etapas del pipeline (en orden)

```
1. PREPARE
   ├── Merge defaultHeaders from NetworkConfig
   ├── Build full URL (baseUrl + path)
   └── Run RequestInterceptor chain (auth, tracing, custom headers)

2. OBSERVE
   └── Notify observers: onRequestStarted

3. RETRY LOOP (governed by RetryPolicy + error.isRetryable)
   │
   ├── 3a. TRANSPORT
   │     └── HttpEngine.execute(request) → RawResponse | Throwable
   │
   ├── 3b. RESPONSE INTERCEPTORS
   │     └── Run ResponseInterceptor chain (logging, caching)
   │
   ├── 3c. OBSERVE
   │     └── Notify observers: onResponseReceived
   │
   ├── 3d. VALIDATE
   │     └── ResponseValidator.validate(response) → Valid | Invalid
   │         • 2xx + Valid → continue
   │         • 2xx + Invalid → ResponseValidation error
   │         • non-2xx → ErrorClassifier.classify() → semantic error
   │
   ├── 3e. DESERIALIZE
   │     └── deserialize(response) → T | Throwable
   │
   └── 3f. RETRY DECISION
         └── If error.isRetryable AND attempts remain → delay → go to 3a
             Notify observers: onRetryScheduled

4. RETURN
   └── NetworkResult.Success(data, metadata) | NetworkResult.Failure(error)
```

### Parámetros del constructor

```kotlin
class DefaultSafeRequestExecutor(
    engine: HttpEngine,                              // Transport
    config: NetworkConfig,                           // Base URL, timeouts, retry policy
    validator: ResponseValidator = DefaultResponseValidator(),
    classifier: ErrorClassifier = DefaultErrorClassifier(),
    interceptors: List<RequestInterceptor> = emptyList(),
    responseInterceptors: List<ResponseInterceptor> = emptyList(),
    observers: List<NetworkEventObserver> = emptyList()
)
```

Todos los parámetros excepto `engine` y `config` tienen valores por defecto razonables. Una configuración mínima requiere solo dos argumentos.

### Invariante crítica

`CancellationException` se **siempre relanza**, nunca se captura, nunca se clasifica. El executor respeta la concurrencia estructurada incondicionalmente. Cada bloque `try`/`catch` en el pipeline verifica explícitamente `CancellationException` primero.

## Consecuencias

### Positivas

- **Consistencia.** Cada request — independientemente del módulo de dominio, endpoint o equipo — pasa por las mismas etapas de preparación, validación, clasificación, reintento y observabilidad.
- **Punto único de extensión.** Agregar métricas requiere pasar un `NetworkEventObserver` al executor. Agregar auth requiere agregar un `RequestInterceptor`. Sin cambios en data sources.
- **Los data sources son triviales.** Un data source típico tiene 15–25 líneas: construir un `HttpRequest`, proveer un lambda `deserialize`, llamar `execute()`. Toda la complejidad está en el executor.
- **El reintento es transparente.** `ResponseMetadata.attemptCount` dice al consumidor cuántos intentos fueron necesarios, sin que el consumidor implemente ninguna lógica de reintento.
- **Testabilidad.** Testear un data source requiere mockear solo `SafeRequestExecutor` — retornar un `NetworkResult.Success` o `NetworkResult.Failure`. Sin servidor HTTP, sin delay de coroutines, sin simulación de reintentos.

### Negativas

- **Punto único de fallo.** Un bug en `DefaultSafeRequestExecutor` afecta todas las requests en todas las apps. Se mitiga con testing exhaustivo (pendiente) y la naturaleza `open` de `DefaultErrorClassifier` / `DefaultResponseValidator`.
- **Sin personalización de pipeline por request.** Todas las requests comparten la misma cadena de interceptors y lista de observers. El comportamiento por request se limita a `RequestContext` (operation ID, override de política de reintento, flag de auth). Una mejora futura podría permitir overrides de interceptors por request si se necesita.
- **Sensibilidad al orden.** Las listas de `RequestInterceptor` y `ResponseInterceptor` están ordenadas. El interceptor de auth debe ejecutarse antes de un interceptor de logging que registre los headers finales. Este orden es implícito — no hay mecanismo de prioridad.
