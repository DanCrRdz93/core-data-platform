# Dependencias de Módulos

Grafo de dependencias que muestra cómo los módulos del proyecto se relacionan entre sí y con librerías externas. Las flechas apuntan desde el dependiente hacia la dependencia.

![Module Dependencies](images/02-module-dependencies.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
graph LR
    subgraph App["Application"]
        APP[":app"]
    end

    subgraph Domain["Domain"]
        SA[":sample-api"]
    end

    subgraph SDK["SDK Modules"]
        direction TB
        NK[":network-ktor"]
        NC[":network-core"]
        SC[":security-core"]
    end

    subgraph External["External Dependencies"]
        direction TB
        KTOR_CORE["ktor-client-core<br/><i>3.0.3</i>"]
        KTOR_OK["ktor-client-okhttp<br/><i>3.0.3 (Android)</i>"]
        KTOR_DAR["ktor-client-darwin<br/><i>3.0.3 (iOS)</i>"]
        SER["kotlinx-serialization-json<br/><i>1.7.3</i>"]
        COROUTINES["kotlinx-coroutines-core<br/><i>1.10.1</i>"]
    end

    APP --> SA
    APP --> NK
    APP --> SC

    SA --> NC
    SA --> NK
    SA --> SC
    SA --> SER

    NK --> NC
    NK --> KTOR_CORE
    NK --> KTOR_OK
    NK --> KTOR_DAR

    NC --> COROUTINES
    SC --> COROUTINES

    style App fill:#f3e5f5,stroke:#7b1fa2
    style Domain fill:#fff3e0,stroke:#ef6c00
    style SDK fill:#e8eaf6,stroke:#3949ab
    style External fill:#fafafa,stroke:#9e9e9e
    style APP fill:#f3e5f5,stroke:#7b1fa2
    style SA fill:#fff3e0,stroke:#ef6c00
    style NK fill:#c8e6c9,stroke:#2e7d32
    style NC fill:#bbdefb,stroke:#1565c0
    style SC fill:#f8bbd0,stroke:#c62828
    style COROUTINES fill:#f5f5f5,stroke:#757575
    style KTOR_CORE fill:#f5f5f5,stroke:#757575
    style KTOR_OK fill:#e8f5e9,stroke:#388e3c
    style KTOR_DAR fill:#fff3e0,stroke:#ef6c00
    style SER fill:#f5f5f5,stroke:#757575
```

</details>

## Roles de Módulos

| Módulo | Rol | Dependencias Externas |
|---|---|---|
| `:network-core` | Abstracciones puras — contratos, pipeline, modelo de errores | Solo `kotlinx-coroutines-core` |
| `:network-ktor` | Adaptador de transporte Ktor | `ktor-client-core`, `ktor-client-okhttp` (Android), `ktor-client-darwin` (iOS) |
| `:security-core` | Abstracciones de seguridad — credenciales, sesiones, almacenamiento, confianza | Solo `kotlinx-coroutines-core` |
| `:sample-api` | Módulo piloto de referencia | `kotlinx-serialization-json` |
| `:app` | Aplicación host | Depende de todos los módulos |

## Invariante Crítica

`:network-core` y `:security-core` tienen **cero dependencia mutua**. Esto se aplica por diseño y debe preservarse. No comparten tipos — ni siquiera `Diagnostic` (que está intencionalmente duplicado como deuda técnica aceptada).
