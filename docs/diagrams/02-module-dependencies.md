# Module Dependencies

Dependency graph showing how the project's modules relate to each other and to external libraries. Arrows point from the dependent to the dependency.

![Module Dependencies](images/02-module-dependencies.svg)

<details>
<summary>Mermaid source</summary>

```mermaid
graph TD
    APP[":app"]
    SA[":sample-api"]
    NK[":network-ktor"]
    NC[":network-core"]
    SC[":security-core"]

    COROUTINES["kotlinx-coroutines-core<br/><i>1.10.1</i>"]
    KTOR_CORE["ktor-client-core<br/><i>3.0.3</i>"]
    KTOR_OK["ktor-client-okhttp<br/><i>3.0.3 (Android)</i>"]
    KTOR_DAR["ktor-client-darwin<br/><i>3.0.3 (iOS)</i>"]
    SER["kotlinx-serialization-json<br/><i>1.7.3</i>"]

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

## Module Roles

| Module | Role | External Dependencies |
|---|---|---|
| `:network-core` | Pure abstractions — contracts, pipeline, error model | `kotlinx-coroutines-core` only |
| `:network-ktor` | Ktor transport adapter | `ktor-client-core`, `ktor-client-okhttp` (Android), `ktor-client-darwin` (iOS) |
| `:security-core` | Security abstractions — credentials, sessions, storage, trust | `kotlinx-coroutines-core` only |
| `:sample-api` | Pilot reference module | `kotlinx-serialization-json` |
| `:app` | Host application | Depends on all modules |

## Critical Invariant

`:network-core` and `:security-core` have **zero mutual dependency**. This is enforced by design and must be preserved. They share no types — not even `Diagnostic` (which is intentionally duplicated as accepted tech debt).
