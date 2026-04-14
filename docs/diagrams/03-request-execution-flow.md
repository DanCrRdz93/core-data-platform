# Flujo de Ejecución de Requests

Ciclo de vida completo de una request de red, desde el consumidor llamando un método del repository hasta recibir un `NetworkResult<T>` con modelos de dominio limpios.

## Diagrama de Secuencia

![Request Execution Flow](images/03-request-execution-flow.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
sequenceDiagram
    participant C as Consumer<br/>(ViewModel)
    participant R as Repository
    participant DS as DataSource<br/>(RemoteDataSource)
    participant E as DefaultSafe<br/>RequestExecutor
    participant RI as Request<br/>Interceptors
    participant H as HttpEngine<br/>(KtorHttpEngine)
    participant RsI as Response<br/>Interceptors
    participant V as Response<br/>Validator
    participant CL as Error<br/>Classifier
    participant O as Observers

    C->>R: getUsers()
    R->>DS: fetchUsers()
    DS->>E: execute(HttpRequest, deserialize)

    Note over E: ① Prepare Request
    E->>E: Merge defaultHeaders + build URL
    E->>RI: intercept(request, context)
    RI-->>E: Modified request (+ auth headers)

    Note over E: ② Notify Start
    E->>O: onRequestStarted(request, context)

    Note over E: ③ Retry Loop
    rect rgb(240, 248, 255)
        E->>H: execute(request)
        alt Transport failure
            H-->>E: throws Throwable
            E->>CL: classify(null, cause)
            CL-->>E: NetworkError
            E->>O: onRequestFailed(...)
        else Success
            H-->>E: RawResponse
        end

        Note over E: ④ Response Interceptors
        E->>RsI: intercept(response, request, context)
        RsI-->>E: Processed response

        E->>O: onResponseReceived(request, response, durationMs)

        Note over E: ⑤ Validate
        E->>V: validate(response)
        alt Valid (2xx)
            V-->>E: ValidationOutcome.Valid
            Note over E: ⑥ Deserialize
            E->>E: deserialize(response) → T
            E-->>DS: NetworkResult.Success(data, metadata)
        else Invalid (non-2xx)
            V-->>E: ValidationOutcome.Invalid
            E->>CL: classify(response, null)
            CL-->>E: NetworkError (semantic)
            E->>O: onRequestFailed(...)
            alt error.isRetryable AND attempts left
                E->>O: onRetryScheduled(attempt, max, error, delayMs)
                E->>E: delay(delayMs)
                Note over E: Retry from ③
            else No retry
                E-->>DS: NetworkResult.Failure(error)
            end
        end
    end

    DS-->>R: NetworkResult&lt;UserDto&gt;
    R->>R: .map(UserMapper::toDomain)
    R-->>C: NetworkResult&lt;User&gt;
```

</details>

## Resumen de Etapas del Pipeline

| Etapa | Componente | Responsabilidad |
|---|---|---|
| ① Preparar | `DefaultSafeRequestExecutor` | Combinar headers, construir URL, ejecutar cadena de `RequestInterceptor` |
| ② Observar | `NetworkEventObserver` | `onRequestStarted` — métricas, trazabilidad |
| ③ Transportar | `HttpEngine` | Enviar request HTTP, recibir respuesta cruda |
| ④ Post-procesar | `ResponseInterceptor` | Logging, caching, extracción de headers |
| ⑤ Validar | `ResponseValidator` | 2xx = válido, no-2xx = clasificar como error |
| ⑥ Deserializar | Lambda provisto por consumidor | `(RawResponse) -> T` |
| ⑦ Reintentar | `RetryPolicy` + `error.isRetryable` | Delay y reintento si aplica |
| ⑧ Retornar | `NetworkResult<T>` | `Success(data, metadata)` o `Failure(error)` |
