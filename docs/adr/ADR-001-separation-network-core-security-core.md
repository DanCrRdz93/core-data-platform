# ADR-001: Separación entre network-core y security-core

## Estado

**Aceptado**

## Contexto

El SDK Core Data Platform necesita proveer tanto capacidades de networking HTTP como funcionalidades de seguridad (gestión de credenciales, almacenamiento seguro, ciclo de vida de sesión, confianza TLS, sanitización de logs). La pregunta de diseño inicial fue si estas deberían vivir en un solo módulo o estar separadas.

Argumentos a favor de un solo módulo:
- Grafo de dependencias más simple.
- Las credenciales y los headers HTTP están estrechamente relacionados en tiempo de ejecución.

Argumentos a favor de la separación:
- Un módulo que solo necesita almacenamiento seguro (ej. un módulo de configuración) no debería traer abstracciones HTTP.
- Un módulo que solo necesita networking (ej. una API pública sin auth) no debería traer abstracciones de seguridad.
- Evolución independiente — las políticas de seguridad cambian con una cadencia diferente a la infraestructura de transporte.
- Testing independiente — los contratos de seguridad pueden validarse sin mockear infraestructura HTTP.
- Propiedad clara en equipos grandes — el equipo de seguridad es dueño de `security-core`, el equipo de plataforma/infra es dueño de `network-core`.

## Decisión

`network-core` y `security-core` son **módulos independientes con cero dependencia mutua**. Ningún módulo importa ningún tipo del otro.

El punto de integración entre ellos es `CredentialHeaderMapper` en `security-core`, que convierte un `Credential` en un simple `Map<String, String>` — sin tipos de red involucrados. El cableado real (adjuntar headers de credenciales a requests HTTP vía `RequestInterceptor`) ocurre en el **módulo consumidor** (ej. `:sample-api`), que depende de ambos.

```
:network-core ──── (no dependency) ──── :security-core
       ▲                                       ▲
       │                                       │
       └──────────── :sample-api ──────────────┘
                  (integration point)
```

## Consecuencias

### Positivas

- **Huella mínima de dependencias.** Un módulo que solo necesita almacenamiento seguro depende únicamente de `security-core` (solo `kotlinx-coroutines` como dependencia transitiva). Sin tipos HTTP, sin políticas de reintento, sin clasificadores de errores.
- **Versionado independiente.** Cambios breaking en el pipeline de ejecución no fuerzan un release de security-core.
- **Testabilidad.** Cada módulo puede testearse de forma aislada con sus propios límites de mock.
- **Superficie de API clara.** Los desarrolladores saben exactamente qué módulo provee qué capacidad.

### Negativas

- **`Diagnostic` está duplicado.** Ambos módulos definen una data class `Diagnostic` idéntica (`description`, `cause`, `metadata`) en diferentes paquetes. Un consumidor que conecte errores entre módulos debe mapear entre ellos manualmente. Esto se acepta como deuda técnica, a resolver con un futuro módulo `:platform-common` cuando 3+ tipos compartidos justifiquen el módulo adicional.
- **La integración requiere cableado explícito.** El interceptor de auth que conecta `CredentialProvider` con `RequestInterceptor` debe escribirse en el módulo consumidor. Esto se mitiga con `CredentialHeaderMapper`, que reduce el cableado a ~3 líneas de código.
- **Sin flujo de errores entre módulos.** Un error HTTP 401 (`NetworkError.Authentication` en `network-core`) no puede disparar directamente una invalidación de sesión (`SessionController` en `security-core`) sin un puente. Esto es por diseño — el puente pertenece a la capa consumidora, no a la base del SDK.
