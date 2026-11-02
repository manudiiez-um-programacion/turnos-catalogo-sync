# Registros de decisiones de arquitectura (ADR)

Un ADR documenta **una** decisión de arquitectura: qué se decidió, en qué
contexto, qué consecuencias trae y qué alternativas se descartaron. Sirve para
dos cosas: que la decisión se pueda defender en la presentación final (§12 del
enunciado) y que una sesión futura no vuelva a discutir algo ya cerrado.

Un ADR no se edita cuando la decisión cambia. Se escribe uno nuevo que la
reemplace y se marca el anterior como `superseded por NNNN`.

## Índice

| # | Título | Estado | Fecha | Issue |
| --- | --- | --- | --- | --- |
| [0001](0001-arquitectura-hexagonal.md) | Arquitectura hexagonal con slices verticales | aceptada | 2026-10-14 | #2 |
| [0002](0002-identidad-y-autenticacion-entre-servicios.md) | Identidad del usuario final y autenticación entre servicios | aceptada | 2026-10-26 | #59 |

Estados posibles: `propuesta`, `aceptada`, `rechazada`, `superseded por NNNN`.

## Convenciones

- Nombre de archivo: `NNNN-slug-corto.md`, numeración correlativa desde `0001`.
- Un ADR por decisión. Si un issue cierra dos decisiones chicas y relacionadas
  (por ejemplo arquitectura y motor de base de datos), pueden ir juntas, pero
  cada una con su sección.
- Se agrega la fila al índice en el mismo commit que crea el ADR.
- Los ADR pendientes del proyecto están listados como decisiones abiertas
  (`D1`–`D8`) en `CLAUDE.md`. Cuando una se cierra, se escribe su ADR y se
  actualiza `CLAUDE.md`.

## Plantilla

```markdown
# ADR NNNN — Título en una línea

- **Estado:** propuesta | aceptada | rechazada | superseded por NNNN
- **Fecha:** AAAA-MM-DD
- **Issue:** #NN
- **Decisiones del proyecto que cierra:** DN (si aplica)

## Contexto

Qué problema hay que resolver y qué restricciones lo condicionan. Citar las
secciones del enunciado o de la referencia de integración que aplican. Sin
contexto, la decisión no se puede evaluar más tarde.

## Decisión

Qué se decide, en presente y en afirmativo. Concreto: nombres de paquetes,
tecnologías, reglas que hay que respetar.

## Consecuencias

### A favor
Qué habilita o simplifica.

### En contra
Qué cuesta. Un ADR sin consecuencias negativas suele ser un ADR mal pensado.

## Alternativas consideradas

Qué más se evaluó y por qué se descartó. Una sección por alternativa.

## Referencias

Secciones del enunciado, de la referencia de integración, issues y skills.
```

## ADRs previstos

- Sincronización e idempotencia (issue #29)
