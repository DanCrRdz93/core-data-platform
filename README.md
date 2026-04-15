# Core Data Platform

![Version](https://img.shields.io/badge/version-0.2.0-blue)
![Maven Central](https://img.shields.io/maven-central/v/io.github.dancrrdz93/network-core)

**SDK Kotlin Multiplatform para Acceso Remoto Seguro de Datos**

Una librería Kotlin Multiplatform (KMP) reutilizable y modular diseñada para proveer una base segura, escalable y agnóstica de transporte para operaciones de datos remotos en aplicaciones Android e iOS.

---

## Tabla de Contenidos

- [Documentación](#documentación)
- [Quick Start](#quick-start)
- [Instalación](#instalación)
- [Resumen](#resumen)
- [Objetivos del Proyecto](#objetivos-del-proyecto)
- [Arquitectura](#arquitectura)
- [Estructura de Módulos](#estructura-de-módulos)
- [Requisitos](#requisitos)
- [Roadmap](#roadmap)
- [Seguridad — OWASP MASVS](#seguridad--owasp-masvs)
- [Reglas de Diseño](#reglas-de-diseño)

---

## Documentación

### Guías

- [Guía de Integración](docs/integration-guide.md) — Paso a paso completo: dependencias, configuración, capa data (todos los métodos HTTP, paginación, filtros, body JSON, 204 No Content), consumo de resultados, RequestContext, ResponseInterceptor, autenticación (simple y con sesión completa), certificate pinning, observabilidad, multi-entorno, y manejo de errores — todo con escenarios de negocio explicados
- [Guía Rápida para Android](docs/android-quickstart.md) — Primeros pasos para desarrolladores Android: contrato, adapter, DI (Hilt/Koin), NetworkResult, errores, auth, config, logging, paginación, retry por endpoint, y FAQ de seguridad
- [Guía Rápida para iOS](docs/ios-quickstart.md) — Primeros pasos para desarrolladores iOS: protocolo, adapter, DI, NetworkResult, errores, auth, config, logging, paginación, retry por endpoint, y FAQ de seguridad
- [Integración con Clean Architecture](docs/clean-architecture-integration.md) — Cómo usar el SDK exclusivamente en la capa `data`: estructura de carpetas, DTOs vs modelos de dominio, DataSource, Repository, DI, y referencias a escenarios avanzados

### READMEs de Módulos

- [network-core](network-core/README.md) — Abstracciones puras de red, pipeline de ejecución, taxonomía de errores
- [network-ktor](network-ktor/README.md) — Adaptador de transporte HTTP basado en Ktor
- [security-core](security-core/README.md) — Credenciales, sesiones, almacenamiento seguro, confianza TLS, sanitización de logs
- [sample-api](sample-api/README.md) — Módulo piloto de referencia para integración de API de dominio

### Registros de Decisiones de Arquitectura (ADRs)

- [Índice de ADRs](docs/adr/README.md)
- [ADR-001: Separación network-core / security-core](docs/adr/ADR-001-separation-network-core-security-core.md)
- [ADR-002: Contratos primero, implementación después](docs/adr/ADR-002-contracts-first-implementation-after.md)
- [ADR-003: Sin detalles de transporte en API pública](docs/adr/ADR-003-no-transport-details-in-public-api.md)
- [ADR-004: Separación commonMain / platformMain](docs/adr/ADR-004-commonmain-platformmain-separation.md)
- [ADR-005: Pipeline de ejecución segura centralizado](docs/adr/ADR-005-centralized-safe-execution-pipeline.md)
- [ADR-006: Clasificación de errores centralizada](docs/adr/ADR-006-centralized-error-classification.md)

### Diagramas

- [Índice de Diagramas](docs/diagrams/README.md)
- [01 — Arquitectura General](docs/diagrams/01-general-architecture.md)
- [02 — Dependencias entre Módulos](docs/diagrams/02-module-dependencies.md)
- [03 — Flujo de Ejecución de Requests](docs/diagrams/03-request-execution-flow.md)
- [04 — Estrategia KMP](docs/diagrams/04-kmp-strategy.md)
- [05 — Relaciones entre Contratos](docs/diagrams/05-contract-relationships.md)

---

## Quick Start

```kotlin
// 1. Crea el repository (configuración por defecto incluida)
val repository = SampleApiFactory.create()

// 2. Llama un endpoint
val result: NetworkResult<List<User>> = repository.getUsers()

// 3. Maneja el resultado
result.fold(
    onSuccess = { users ->
        users.forEach { println("${it.displayName} (@${it.handle})") }
    },
    onFailure = { error ->
        println("Error: ${error.message}")
    }
)
```

> `SampleApiFactory.create()` ensambla internamente todo el pipeline: configuración → engine HTTP → executor con reintentos → data source → repository. Tú solo interactúas con `UserRepository` y `NetworkResult<User>`.

Para configuración avanzada (auth, timeouts personalizados, interceptors), consulta la [Guía de Integración](docs/integration-guide.md) o las guías rápidas para [Android](docs/android-quickstart.md) / [iOS](docs/ios-quickstart.md).

---

## Instalación

El SDK está publicado en **Maven Central**. Agrega los módulos que necesites en tu `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Contratos core (siempre requeridos)
    implementation("io.github.dancrrdz93:network-core:0.2.0")

    // Implementación de transporte HTTP (elige uno)
    implementation("io.github.dancrrdz93:network-ktor:0.2.0")

    // Seguridad (auth, almacenamiento seguro, gestión de sesiones)
    implementation("io.github.dancrrdz93:security-core:0.2.0")

    // Módulo de referencia (opcional — para ver el patrón de integración)
    implementation("io.github.dancrrdz93:sample-api:0.2.0")
}
```

Si el SDK se consume como módulo local (composite build), usa `implementation(project(":network-core"))` en su lugar.

---

## Resumen

Core Data Platform es un SDK interno construido con Kotlin Multiplatform que provee una base unificada, segura y extensible para hacer llamadas a APIs remotas desde aplicaciones móviles. Está diseñado para ser consumido por múltiples apps a gran escala sin acoplarlas a ningún cliente HTTP, librería de serialización o contrato de backend específico.

### ¿Qué problema resuelve?

En organizaciones que mantienen múltiples aplicaciones móviles, cada equipo tiende a construir su propio stack de networking y seguridad. Esto lleva a:

- **Infraestructura duplicada** — lógica de reintentos, manejo de errores y flujos de auth reimplementados por app.
- **Manejo de errores inconsistente** — cada app clasifica y muestra errores de forma diferente.
- **Fragmentación de seguridad** — almacenamiento de credenciales, sanitización de logs y políticas TLS varían entre equipos.
- **Testing difícil** — código de networking fuertemente acoplado hace que el unit testing sea costoso.

Core Data Platform resuelve esto proveyendo una **única base bien testeada y dirigida por contratos** que todas las apps comparten, manteniendo cada app libre de definir su propia lógica de dominio, serialización y UI.

### ¿Dónde puede usarse?

- Organizaciones móviles multi-app (banca, fintech, seguros, retail, salud)
- Equipos adoptando Kotlin Multiplatform para lógica de negocio compartida
- Cualquier proyecto que necesite una separación limpia entre infraestructura de transporte y lógica de dominio

---

## Objetivos del Proyecto

| Objetivo | Cómo se logra |
|---|---|
| **Reutilizable** | Contratos puros en `network-core` y `security-core` — sin lógica específica de app, sin suposiciones de backend |
| **Desacoplado** | `network-core` tiene cero conocimiento de Ktor, OkHttp o cualquier librería HTTP. El transporte es pluggable vía `HttpEngine` |
| **Seguro** | Abstracción de credenciales, almacenamiento seguro de plataforma, sanitización de logs, políticas de confianza TLS — todo como contratos de primera clase |
| **Escalable** | Nuevos módulos de dominio (pagos, fidelidad, etc.) se agregan sin modificar módulos core |
| **Portable** | Kotlin Multiplatform con contratos en `commonMain` e implementaciones de plataforma en `androidMain`/`iosMain` |
| **Mantenible** | Interfaces pequeñas y enfocadas. Clases open para extensión. Tipos sealed para manejo exhaustivo. Sin God objects |

---

## Arquitectura

### Filosofía de Diseño

La arquitectura sigue tres principios fundamentales:

1. **Contratos sobre implementaciones** — Cada componente principal se define como una interfaz o clase abstracta en `commonMain`. Las implementaciones concretas se inyectan, nunca se hardcodean.

2. **Separación por capas** — El proyecto separa *qué* hace el SDK (contratos) de *cómo* lo hace (implementaciones) y *quién* lo usa (módulos de dominio).

3. **Cero acoplamiento lateral** — `network-core` y `security-core` son módulos completamente independientes. Ninguno sabe que el otro existe. Solo se componen en el punto de consumo (módulos de dominio o la app).

### ¿Por qué están separados `network-core` y `security-core`?

Estos módulos abordan preocupaciones fundamentalmente diferentes:

- **`network-core`** responde: *"¿Cómo ejecuto, valido, reintento y clasifico operaciones HTTP de forma segura?"*
- **`security-core`** responde: *"¿Cómo almaceno secretos, gestiono sesiones, evalúo confianza y protejo datos sensibles?"*

Mantenerlos independientes significa:

- Un módulo que solo necesita almacenamiento seguro no trae dependencias HTTP.
- Un módulo que solo necesita networking no trae dependencias de seguridad.
- El punto de integración (inyección de credenciales en headers HTTP) se maneja con un mapper ligero (`CredentialHeaderMapper`) que vive en `security-core` y retorna un simple `Map<String, String>` — sin tipos de red requeridos.

### ¿Cómo encaja en aplicaciones grandes?

```
┌──────────────────────────────────────────────────────────────┐
│                      YOUR APPLICATION                        │
│                                                              │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                   │
│   │ Feature A│  │ Feature B│  │ Feature C│  ← App layers     │
│   └────┬─────┘  └─────┬────┘  └──────┬───┘                   │
│        │              │              │                       │
│   ┌────▼──────────────▼──────────────▼────┐                  │
│   │        Domain API Modules             │  ← :payments-api │
│   │   (DTOs, Mappers, DataSources, Repos) │     :loyalty-api │
│   └────┬──────────────┬──────────────┬────┘     :users-api   │
│        │              │              │                       │
├────────┼──────────────┼──────────────┼───────────────────────┤
│        ▼              ▼              ▼                       │
│   ┌──────────┐  ┌────────────┐  ┌──────────────┐             │
│   │ network  │  │  network   │  │  security    │  ← SDK      │
│   │  -core   │  │   -ktor    │  │   -core      │             │
│   └──────────┘  └────────────┘  └──────────────┘             │
└──────────────────────────────────────────────────────────────┘
```

Los módulos del SDK se sitúan en la parte inferior del grafo de dependencias. Las funcionalidades de la aplicación nunca importan Ktor, nunca ven `RawResponse`, y nunca manejan lógica de reintentos directamente.

---

## Estructura de Módulos

| Módulo | Responsabilidad | Contratos clave | Dependencias |
|---|---|---|---|
| **`:network-core`** | Abstracciones puras de red: ejecución HTTP, errores, validación, reintentos, observabilidad | `HttpEngine`, `SafeRequestExecutor`, `NetworkResult<T>`, `NetworkError`, `NetworkEventObserver`, `LoggingObserver`, `NetworkLogger`, `RetryPolicy` | Solo `kotlinx-coroutines-core` |
| **`:network-ktor`** | Adaptador de transporte HTTP. Encapsula Ktor completamente | `KtorHttpEngine`, `KtorErrorClassifier` | `:network-core`, `ktor-client-core`, `ktor-client-okhttp` (Android), `ktor-client-darwin` (iOS) |
| **`:security-core`** | Credenciales, sesiones, almacenamiento seguro, confianza TLS, sanitización de logs | `Credential`, `CredentialProvider`, `SessionController`, `SecretStore`, `TrustPolicy`, `LogSanitizer` | Solo `kotlinx-coroutines-core` |
| **`:sample-api`** | Módulo piloto de referencia: demuestra el patrón DTO → Mapper → DataSource → Repository → Factory | `UserDto`, `User`, `UserMapper`, `UserRemoteDataSource`, `UserRepository`, `SampleApiFactory` | `:network-core`, `:network-ktor`, `:security-core`, `kotlinx-serialization-json` |

**Implementaciones de plataforma en `:security-core`:**
- `AndroidSecretStore` — DataStore + Cipher(AES/GCM/NoPadding) + Android Keystore
- `IosSecretStore` — Keychain Services (`kSecClassGenericPassword`)
- `DefaultSessionController` — Gestión de sesión con `StateFlow`, persistencia en `SecretStore`, refresh configurable
- `DefaultCredentialProvider` — Lee credencial activa desde `SessionController.state`

> Para el detalle completo de cada módulo (tablas Expone/NO expone, contratos internos, decisiones de diseño), consulta los [READMEs de Módulos](#readmes-de-módulos).

---

### Grafo de Dependencias de Módulos

```
:sample-api ──▶ :network-core
:sample-api ──▶ :network-ktor ──▶ :network-core
:sample-api ──▶ :security-core

:network-ktor ──▶ :network-core

:network-core ──▶ (ninguno)
:security-core ──▶ (ninguno)
```

**Invariante crítica:** `:network-core` y `:security-core` tienen **cero dependencia mutua**. Esto es por diseño y debe preservarse.

---

El 95%+ del código vive en `commonMain`. Solo `SecretStore` (Android Keystore / iOS Keychain) y los engines de transporte HTTP (OkHttp / Darwin) requieren source sets de plataforma. Para más detalle, consulta la [Estrategia KMP](docs/diagrams/04-kmp-strategy.md).

---

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| **Kotlin** | 2.1.20 | Plugin Kotlin Multiplatform |
| **Gradle** | 9.3.1+ | Con catálogo de versiones (`libs.versions.toml`) |
| **AGP** | 9.1.0 | Usa plugin `com.android.kotlin.multiplatform.library` |
| **Android Studio** | Ladybug o posterior | Requiere soporte KMP |
| **Xcode** | 15+ | Para compilación de target iOS |
| **Android `compileSdk`** | 36 | |
| **Android `minSdk`** | 29 | Android 10+ |
| **Targets iOS** | `iosX64`, `iosArm64`, `iosSimulatorArm64` | |

### Dependencias clave

| Librería | Versión | Módulo |
|---|---|---|
| `kotlinx-coroutines-core` | 1.10.1 | `network-core`, `security-core` |
| `ktor-client-core` | 3.0.3 | `network-ktor` |
| `ktor-client-okhttp` | 3.0.3 | `network-ktor` (Android) |
| `ktor-client-darwin` | 3.0.3 | `network-ktor` (iOS) |
| `kotlinx-serialization-json` | 1.7.3 | `sample-api` (módulos de dominio) |

---

Para guías detalladas de integración, configuración y uso:

- **Android →** [Guía Rápida para Android](docs/android-quickstart.md)
- **iOS →** [Guía Rápida para iOS](docs/ios-quickstart.md)
- **Integración paso a paso →** [Guía de Integración](docs/integration-guide.md)
- **Clean Architecture →** [Integración con Clean Architecture](docs/clean-architecture-integration.md)

Para detalles técnicos de cada módulo (contratos, decisiones de diseño, extensibilidad, manejo de errores, seguridad):

- [network-core](network-core/README.md) — Pipeline de ejecución, errores, observabilidad
- [network-ktor](network-ktor/README.md) — Transporte HTTP, certificate pinning
- [security-core](security-core/README.md) — Credenciales, sesiones, almacenamiento seguro, TrustPolicy
- [sample-api](sample-api/README.md) — Módulo de referencia

Para diagramas de arquitectura, consulta el [índice de diagramas](docs/diagrams/README.md).

---

## Roadmap

### Fase 1 — Funcionalidad Core ✅

| Tarea | Módulo | Estado |
|---|---|---|
| Implementar `AndroidSecretStore` con DataStore + Cipher + KeyStore | `security-core` | ✅ Completado |
| Implementar `IosSecretStore` con Keychain Services | `security-core` | ✅ Completado |
| Implementar `DefaultSessionController` con StateFlow + almacenamiento de tokens | `security-core` | ✅ Completado |
| Implementar `DefaultCredentialProvider` respaldado por SessionController | `security-core` | ✅ Completado |
| `RefreshOutcome` sealed — migrar `refreshSession()` de `Boolean` a resultado tipado | `security-core` | ✅ Completado |
| `SessionController.invalidate()` + `isAuthenticated` — force-logout y conveniencia | `security-core` | ✅ Completado |
| `CredentialProvider.refresh()` + `invalidate()` — refresh proactivo e invalidación en 401 | `security-core` | ✅ Completado |

### Fase 2 — Observabilidad y Testing

| Tarea | Módulo | Estado |
|---|---|---|
| `LoggingObserver` + `NetworkLogger` — logging del ciclo de vida de requests con sanitización | `network-core` | ✅ Completado |
| `MetricsObserver` + `MetricsCollector` — latencia, errores, retries con backend inyectable (NOOP default) | `network-core` | ✅ Completado |
| Tests de integración con Ktor `MockEngine` | `network-ktor` | 🔴 No iniciado |
| Tests unitarios para `DefaultSafeRequestExecutor` | `network-core` | 🔴 No iniciado |

### Fase 3 — Seguridad Avanzada ✅

| Tarea | Módulo | Estado |
|---|---|---|
| Certificate pinning vía `TrustPolicy` → OkHttp / Darwin TLS | `network-ktor` | ✅ Completado |
| Interceptor de auth refresh (401 → refresh → retry) | Módulo puente | ✅ Habilitado por `CredentialProvider.invalidate()` |
| Logging de respuestas sanitizado con `LogSanitizer` | `network-core` (vía `headerSanitizer` lambda) | ✅ Completado |

### Fase 4 — Escala

| Tarea | Módulo | Estado |
|---|---|---|
| Unificar `Diagnostic` en módulo `:platform-common` | Nuevo módulo | 🔴 No iniciado |
| `TracingObserver` + `TracingBackend` — span/trace IDs con backend inyectable (NOOP default) | `network-core` | ✅ Completado |
| `HttpEngine.healthCheck()` — liveness probing (default true, implementado en KtorHttpEngine) | `network-core` / `network-ktor` | ✅ Completado |
| `SecretStore.keys()` + `putStringIfAbsent()` — migración/diagnóstico y escritura atómica | `security-core` | ✅ Completado |
| Cleanup de TODOs redundantes — reclasificación de logging/caching/circuit-breaker | Todos | ✅ Completado |
| Publicación en Maven Central | Todos | ✅ Completado (v0.2.0) |
| Política de reintento circuit breaker | `network-core` | 🔴 No iniciado |
| Primer módulo de dominio en producción | Nuevo módulo | 🔴 No iniciado |

---

## Seguridad — OWASP MASVS

El SDK implementa guardrails de seguridad alineados al [OWASP Mobile Application Security Verification Standard (MASVS)](https://mas.owasp.org/MASVS/).
Consulta [`docs/security-checklist.md`](docs/security-checklist.md) para el checklist completo.

| Categoría MASVS | Guardrail | Módulo |
|---|---|---|
| **NETWORK-1** — Solo HTTPS | `NetworkConfig` rechaza `http://` por defecto | `network-core` |
| **NETWORK-2** — Certificate Pinning | `TrustPolicy` → OkHttp / Darwin TLS | `network-ktor` |
| **STORAGE-1** — Almacenamiento seguro | `SecretStore` → DataStore + Cipher + Keystore / Keychain | `security-core` |
| **STORAGE-2** — No secretos en logs | `toString()` redactado en `Credential`, `HttpRequest`, `RawResponse`, `SessionCredentials` | `security-core`, `network-core` |
| **PRIVACY-1** — Header sanitization | `LoggingObserver` redacta ALL headers por defecto | `network-core` |
| **PRIVACY-2** — Query params en observabilidad | `MetricsObserver` / `TracingObserver` strip query params | `network-core` |
| **AUTH-1** — Credential lifecycle | `SessionController` con invalidación, refresh, limpieza en logout | `security-core` |

### Principio de diseño

> **El camino seguro es el camino por defecto.**
> Los guardrails están activos sin configuración. Desactivarlos requiere acción explícita (`allowInsecureConnections`, sanitizer custom).

---

## Reglas de Diseño

Estas son las invariantes arquitectónicas del proyecto. Todas las contribuciones deben respetarlas.

1. **El API público nunca expone detalles de transporte.**
   Los consumidores ven `HttpRequest`, `NetworkResult`, `NetworkError`. Nunca `io.ktor.*`, `okhttp3.*`, ni `NSURLSession`.

2. **`network-core` y `security-core` nunca deben depender el uno del otro.**
   Solo se integran a nivel del consumidor vía composición.
   Excepción: `network-ktor` depende de `security-core` para configurar certificate pinning via `TrustPolicy`.

3. **Los errores son valores, no excepciones.**
   `NetworkResult.Failure` envuelve `NetworkError`. Las excepciones se capturan en el límite del executor y se clasifican en tipos semánticos.

4. **`Diagnostic` es interno. `message` es público.**
   `error.message` es seguro para usuarios finales. `error.diagnostic` es solo para logging y debugging.

5. **Las decisiones de reintento pertenecen al modelo de error.**
   `NetworkError.isRetryable` determina la reintentabilidad. El executor no hardcodea qué errores reintentar.

6. **Los interceptors son para infraestructura, no lógica de negocio.**
   Headers de auth, contexto de tracing, logging — sí. Validación de órdenes, cálculo de precios — no.

7. **DTOs y modelos de dominio siempre están separados.**
   Los DTOs tienen `@Serializable` y coinciden con el contrato del API. Los modelos de dominio son limpios y agnósticos del API.

8. **El código específico de plataforma vive en los bordes.**
   Interfaces en `commonMain`. Implementaciones en `androidMain`/`iosMain`. Nunca al revés.

9. **Cada componente principal es inyectable.**
   `HttpEngine`, `ErrorClassifier`, `ResponseValidator`, `CredentialProvider`, `SecretStore` — todas interfaces, todas inyectadas vía constructor.

10. **Las nuevas funcionalidades son aditivas.**
    Nuevos módulos, nuevos interceptors, nuevos observers, nuevos subtipos de error. Los contratos existentes son estables.

---

*Core Data Platform — Construido para equipos que envían múltiples apps desde una base compartida.*
