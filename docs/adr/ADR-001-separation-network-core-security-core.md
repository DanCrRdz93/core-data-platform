# ADR-001: Separation Between network-core and security-core

## Status

**Accepted**

## Context

The Core Data Platform SDK needs to provide both HTTP networking capabilities and security features (credential management, secure storage, session lifecycle, TLS trust, log sanitization). The initial design question was whether these should live in a single module or be separated.

Arguments for a single module:
- Simpler dependency graph.
- Credentials and HTTP headers are closely related at runtime.

Arguments for separation:
- A module that only needs secure storage (e.g., a settings module) should not pull in HTTP abstractions.
- A module that only needs networking (e.g., a public API with no auth) should not pull in security abstractions.
- Independent evolution — security policies change on a different cadence than transport infrastructure.
- Independent testing — security contracts can be validated without mocking HTTP infrastructure.
- Clearer ownership in larger teams — security team owns `security-core`, platform/infra team owns `network-core`.

## Decision

`network-core` and `security-core` are **independent modules with zero mutual dependency**. Neither module imports any type from the other.

The integration point between them is `CredentialHeaderMapper` in `security-core`, which converts a `Credential` into a plain `Map<String, String>` — no network types involved. The actual wiring (attaching credential headers to HTTP requests via `RequestInterceptor`) happens in the **consuming module** (e.g., `:sample-api`), which depends on both.

```
:network-core ──── (no dependency) ──── :security-core
       ▲                                       ▲
       │                                       │
       └──────────── :sample-api ──────────────┘
                  (integration point)
```

## Consequences

### Positive

- **Minimal dependency footprint.** A module needing only secure storage depends on `security-core` alone (only `kotlinx-coroutines` as transitive dependency). No HTTP types, no retry policies, no error classifiers pulled in.
- **Independent versioning.** Breaking changes in the execution pipeline do not force a security-core release.
- **Testability.** Each module can be tested in isolation with its own mock boundaries.
- **Clear API surface.** Developers know exactly which module provides which capability.

### Negative

- **`Diagnostic` is duplicated.** Both modules define an identical `Diagnostic` data class (`description`, `cause`, `metadata`) in different packages. A consumer that bridges errors across modules must map between them manually. This is accepted as tech debt, to be resolved by a future `:platform-common` module when 3+ shared types justify the additional module.
- **Integration requires explicit wiring.** The auth interceptor that connects `CredentialProvider` to `RequestInterceptor` must be written in the consuming module. This is mitigated by `CredentialHeaderMapper`, which reduces the wiring to ~3 lines of code.
- **No cross-module error flow.** A 401 HTTP error (`NetworkError.Authentication` in `network-core`) cannot directly trigger a session invalidation (`SessionController` in `security-core`) without a bridge. This is by design — the bridge belongs in the consuming layer, not in the SDK foundation.
