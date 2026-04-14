# General Architecture

Overview of how the Core Data Platform SDK fits within a consuming application. The SDK provides the foundation layer (networking + security), domain API modules sit on top, and application features consume clean domain models without knowledge of transport or security internals.

![General Architecture](images/01-general-architecture.svg)

<details>
<summary>Mermaid source</summary>

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

## Key Principles

- **Application features** never import SDK types directly. They receive clean domain models (`User`, `Order`, `Payment`) from repositories.
- **Domain API modules** are the integration point. They depend on both `:network-core` and `:security-core`, composing them via factories.
- **`:network-core` and `:security-core` are independent.** Neither imports the other. They are composed only at the domain module level.
- **`:network-ktor` is replaceable.** It is the only module that imports Ktor. Swapping it requires zero changes to any other module.
