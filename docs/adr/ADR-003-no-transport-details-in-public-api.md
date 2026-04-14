# ADR-003: Sin detalles de transporte en el API público

## Estado

**Aceptado**

## Contexto

El SDK usa [Ktor 3.0.3](https://ktor.io/) como transporte HTTP vía el módulo `:network-ktor`. Ktor provee tipos como `HttpClient`, `HttpResponse`, `HttpRequestBuilder`, `HttpRequestTimeoutException`, y clases específicas de engine (`OkHttpConfig`, `DarwinClientEngineConfig`).

Si los módulos de dominio (`:sample-api`, futuro `:payments-api`, etc.) o las aplicaciones consumidoras importan tipos de Ktor directamente, surgen varios problemas:

- **Vendor lock-in.** Reemplazar Ktor con OkHttp, URLSession, o un engine custom requeriría cambios en cada consumidor.
- **Abstracciones con fugas.** Los consumidores necesitarían entender el DSL de configuración de Ktor, la selección de engine y la jerarquía de excepciones.
- **Fricción en testing.** Mockear tipos de Ktor es significativamente más complejo que mockear una simple interfaz `HttpEngine`.
- **Riesgo de compatibilidad binaria.** Un upgrade de versión major de Ktor sería un cambio breaking para todos los consumidores.

## Decisión

Ningún módulo fuera de `:network-ktor` puede importar ningún tipo `io.ktor.*`. La superficie del API público usa exclusivamente tipos definidos por el SDK:

| El consumidor ve | Equivalente Ktor (oculto) |
|---|---|
| `HttpEngine` | `HttpClient` |
| `HttpRequest` | `HttpRequestBuilder` |
| `RawResponse` | `HttpResponse` |
| `HttpMethod` (enum) | `io.ktor.http.HttpMethod` |
| `NetworkError.Timeout` | `HttpRequestTimeoutException` |
| `NetworkConfig` (timeouts) | `HttpTimeout` plugin config |
| `SafeRequestExecutor` | *(no Ktor equivalent)* |

El límite de traducción vive en `KtorHttpEngine`:

```
SDK types                        Ktor types
──────────                       ──────────
HttpRequest ──── KtorHttpEngine ──── HttpRequestBuilder
RawResponse ◀─── KtorHttpEngine ◀─── HttpResponse
HttpMethod  ──── toKtor()       ──── io.ktor.http.HttpMethod
```

`KtorErrorClassifier` traduce `HttpRequestTimeoutException` a `NetworkError.Timeout` para que ningún tipo de excepción de Ktor escape del módulo de transporte.

### Regla de verificación

Un simple grep puede verificar esta invariante en cualquier momento:

```bash
# Debe retornar cero resultados para todos los módulos excepto network-ktor
grep -r "io.ktor" network-core/ security-core/ sample-api/
```

## Consecuencias

### Positivas

- **El transporte es reemplazable.** Crear `:network-okhttp` requiere solo implementar `HttpEngine` y `ErrorClassifier`. Sin cambios en código consumidor.
- **Testing simplificado.** Los módulos de dominio testean contra `SafeRequestExecutor` (un `suspend fun` que retorna `NetworkResult<T>`). Sin `MockEngine`, sin mockear plugins de Ktor.
- **Upgrades de Ktor aislados.** Una migración Ktor 3→4 afecta exactamente un módulo (`:network-ktor`). Todos los demás módulos quedan intactos.
- **Superficie de API consistente.** Los consumidores interactúan con los mismos tipos independientemente de qué librería HTTP se use por debajo.

### Negativas

- **Overhead de traducción.** Cada request se traduce de `HttpRequest` → builder de Ktor, y cada respuesta de `HttpResponse` de Ktor → `RawResponse`. Esto implica copiar headers y leer el body como `ByteArray`. El costo es negligible para payloads típicos de API pero importaría para respuestas muy grandes (>10 MB), donde se necesitaría streaming.
- **Brecha de funcionalidades.** Funcionalidades de Ktor que no tienen equivalente en el SDK (WebSockets, SSE, uploads multipart) no son accesibles a través de la abstracción actual. Estas requieren extender `HttpEngine` o definir contratos paralelos.
- **Doble configuración.** Los valores de timeout fluyen de `NetworkConfig` → `KtorHttpEngine.create()` → plugin `HttpTimeout` de Ktor. No hay forma de configurar funcionalidades específicas de Ktor (ej. tamaño del pool de conexiones) a través de `NetworkConfig` hoy.
