# Registros de Decisiones Arquitectónicas

Este directorio contiene los Architecture Decision Records (ADRs) del SDK Core Data Platform. Cada ADR documenta una decisión arquitectónica significativa, su contexto y sus consecuencias.

## Índice

| ADR | Título | Estado |
|---|---|---|
| [ADR-001](ADR-001-separation-network-core-security-core.md) | Separación entre network-core y security-core | Aceptado |
| [ADR-002](ADR-002-contracts-first-implementation-after.md) | Contratos primero, implementación después | Aceptado |
| [ADR-003](ADR-003-no-transport-details-in-public-api.md) | Sin detalles de transporte en el API público | Aceptado |
| [ADR-004](ADR-004-commonmain-platformmain-separation.md) | Estrategia de separación commonMain / platformMain | Aceptado |
| [ADR-005](ADR-005-centralized-safe-execution-pipeline.md) | Pipeline de ejecución segura centralizado | Aceptado |
| [ADR-006](ADR-006-centralized-error-classification.md) | Clasificación centralizada de errores | Aceptado |

## Formato

Cada ADR sigue el formato estándar:

- **Título** — Nombre descriptivo corto
- **Estado** — Aceptado, Reemplazado o Deprecado
- **Contexto** — El problema y las fuerzas en juego
- **Decisión** — Qué se decidió y por qué
- **Consecuencias** — Resultados positivos y negativos

## Contribuir

Al tomar una decisión arquitectónica significativa:

1. Crea un nuevo archivo: `ADR-NNN-titulo-corto.md`
2. Sigue el formato anterior
3. Agrégalo a la tabla del índice
4. Referencia el ADR en el README del módulo relevante si aplica
