# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for the Core Data Platform SDK. Each ADR documents a significant architectural decision, its context, and its consequences.

## Index

| ADR | Title | Status |
|---|---|---|
| [ADR-001](ADR-001-separation-network-core-security-core.md) | Separation Between network-core and security-core | Accepted |
| [ADR-002](ADR-002-contracts-first-implementation-after.md) | Contracts First, Implementation After | Accepted |
| [ADR-003](ADR-003-no-transport-details-in-public-api.md) | No Transport Details in the Public API | Accepted |
| [ADR-004](ADR-004-commonmain-platformmain-separation.md) | commonMain / platformMain Separation Strategy | Accepted |
| [ADR-005](ADR-005-centralized-safe-execution-pipeline.md) | Centralized Safe Execution Pipeline | Accepted |
| [ADR-006](ADR-006-centralized-error-classification.md) | Centralized Error Classification | Accepted |

## Format

Each ADR follows the standard format:

- **Title** — Short descriptive name
- **Status** — Accepted, Superseded, or Deprecated
- **Context** — The problem and forces at play
- **Decision** — What was decided and why
- **Consequences** — Positive and negative outcomes

## Contributing

When making a significant architectural decision:

1. Create a new file: `ADR-NNN-short-title.md`
2. Follow the format above
3. Add it to the index table
4. Reference the ADR in the relevant module README if applicable
