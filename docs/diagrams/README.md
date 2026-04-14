# Diagramas de Arquitectura

Documentación visual del SDK Core Data Platform. Todos los diagramas están renderizados como **imágenes SVG** committeadas al repositorio, con archivos fuente Mermaid disponibles para edición.

## Índice

| Documento | Diagramas | Descripción |
|---|---|---|
| [01-general-architecture](01-general-architecture.md) | Vista general del sistema | Cómo el SDK encaja dentro de las aplicaciones consumidoras — capas, módulos y límites |
| [02-module-dependencies](02-module-dependencies.md) | Grafo de dependencias | Todos los módulos y librerías externas con sus relaciones de dependencia |
| [03-request-execution-flow](03-request-execution-flow.md) | Diagrama de secuencia | Ciclo de vida completo de una request: preparar → interceptar → transportar → validar → deserializar → reintentar |
| [04-kmp-strategy](04-kmp-strategy.md) | Distribución de source sets + flowchart de decisión | Qué va en `commonMain` vs `androidMain` vs `iosMain` |
| [05-contract-relationships](05-contract-relationships.md) | 4 diagramas de clases | Pipeline de ejecución, modelo de resultado/error, contratos de seguridad e integración cross-module |

## Imágenes Generadas

Todos los archivos SVG en `images/` se generan a partir de los archivos fuente `.mmd` en `mmd/`:

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

## Regenerar Diagramas

Si editas un archivo fuente `.mmd`, regenera el SVG correspondiente:

```bash
# Un solo diagrama
mmdc -i docs/diagrams/mmd/01-general-architecture.mmd \
     -o docs/diagrams/images/01-general-architecture.svg \
     -b transparent

# Todos los diagramas
for f in docs/diagrams/mmd/*.mmd; do
  name=$(basename "$f" .mmd)
  mmdc -i "$f" -o "docs/diagrams/images/${name}.svg" -b transparent
done
```

Requiere `@mermaid-js/mermaid-cli`:

```bash
npm install -g @mermaid-js/mermaid-cli
```
