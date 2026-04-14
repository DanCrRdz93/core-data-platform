# Architecture Diagrams

Visual documentation for the Core Data Platform SDK. All diagrams are rendered as **SVG images** committed to the repository, with Mermaid source files available for editing.

## Index

| Document | Diagrams | Description |
|---|---|---|
| [01-general-architecture](01-general-architecture.md) | System overview | How the SDK fits within consuming applications — layers, modules, and boundaries |
| [02-module-dependencies](02-module-dependencies.md) | Dependency graph | All modules and external libraries with their dependency relationships |
| [03-request-execution-flow](03-request-execution-flow.md) | Sequence diagram | Complete lifecycle of a request: prepare → intercept → transport → validate → deserialize → retry |
| [04-kmp-strategy](04-kmp-strategy.md) | Source set distribution + decision flowchart | What goes in `commonMain` vs `androidMain` vs `iosMain` |
| [05-contract-relationships](05-contract-relationships.md) | 4 class diagrams | Execution pipeline, result/error model, security contracts, and cross-module integration |

## Generated Images

All SVG files in `images/` are generated from the `.mmd` source files in `mmd/`:

| Image | Source |
|---|---|
| `images/01-general-architecture.svg` | `mmd/01-general-architecture.mmd` |
| `images/02-module-dependencies.svg` | `mmd/02-module-dependencies.mmd` |
| `images/03-request-execution-flow.svg` | `mmd/03-request-execution-flow.mmd` |
| `images/04-kmp-strategy.svg` | `mmd/04-kmp-strategy.mmd` |
| `images/04b-decision-rules.svg` | `mmd/04b-decision-rules.mmd` |
| `images/05a-execution-pipeline.svg` | `mmd/05a-execution-pipeline.mmd` |
| `images/05b-result-error-model.svg` | `mmd/05b-result-error-model.mmd` |
| `images/05c-security-contracts.svg` | `mmd/05c-security-contracts.mmd` |
| `images/06-cross-module-integration.svg` | `mmd/06-cross-module-integration.mmd` |
| `images/05-contract-relationships.svg` | `mmd/05-contract-relationships.mmd` |

## Regenerating Diagrams

If you edit a `.mmd` source file, regenerate the corresponding SVG:

```bash
# Single diagram
mmdc -i docs/diagrams/mmd/01-general-architecture.mmd \
     -o docs/diagrams/images/01-general-architecture.svg \
     -b transparent

# All diagrams
for f in docs/diagrams/mmd/*.mmd; do
  name=$(basename "$f" .mmd)
  mmdc -i "$f" -o "docs/diagrams/images/${name}.svg" -b transparent
done
```

Requires `@mermaid-js/mermaid-cli`:

```bash
npm install -g @mermaid-js/mermaid-cli
```
