# Arquitectura General

Vista general de cómo el SDK Core Data Platform encaja dentro de una aplicación consumidora. El SDK provee la capa base (networking + seguridad), los módulos de dominio API se sitúan encima, y las funcionalidades de la aplicación consumen modelos de dominio limpios sin conocimiento de los detalles internos de transporte o seguridad.

![General Architecture](images/01-general-architecture.svg)

<details>
<summary>Código fuente Mermaid</summary>

```mermaid
graph TD
    subgraph Application["Consuming Application"]
        F1["Feature A<br/>(ViewModel / UI)"]
        F2["Feature B<br/>(ViewModel / UI)"]
        F3["Feature C<br/>(ViewModel / UI)"]
    end

    subgraph Domain["Domain API Modules"]
        DA[":payments-api"]
        DB[":loyalty-api"]
        DC[":sample-api"]
    end

    subgraph SDK["Core Data Platform SDK"]
        subgraph Network["Network Layer"]
            NC[":network-core<br/><i>Contracts, pipeline,<br/>error model, retry</i>"]
            NK[":network-ktor<br/><i>Ktor transport adapter</i>"]
        end

        subgraph Security["Security Layer"]
            SC[":security-core<br/><i>Credentials, sessions,<br/>storage, trust, sanitization</i>"]
        end
    end

    F1 --> DA
    F2 --> DB
    F3 --> DC

    DA --> NC
    DA --> NK
    DA --> SC
    DB --> NC
    DB --> NK
    DB --> SC
    DC --> NC
    DC --> NK
    DC --> SC

    NK --> NC

    style Application fill:#f3e5f5,stroke:#7b1fa2
    style Domain fill:#fff3e0,stroke:#ef6c00
    style Network fill:#e1f5fe,stroke:#0277bd
    style Security fill:#fce4ec,stroke:#c62828
    style NC fill:#bbdefb,stroke:#1565c0
    style NK fill:#c8e6c9,stroke:#2e7d32
    style SC fill:#f8bbd0,stroke:#c62828
```

</details>

## Principios Clave

- **Las funcionalidades de la aplicación** nunca importan tipos del SDK directamente. Reciben modelos de dominio limpios (`User`, `Order`, `Payment`) de los repositories.
- **Los módulos de dominio API** son el punto de integración. Dependen tanto de `:network-core` como de `:security-core`, componiéndolos vía factories.
- **`:network-core` y `:security-core` son independientes.** Ninguno importa al otro. Solo se componen a nivel de módulo de dominio.
- **`:network-ktor` es reemplazable.** Es el único módulo que importa Ktor. Intercambiarlo requiere cero cambios en cualquier otro módulo.
