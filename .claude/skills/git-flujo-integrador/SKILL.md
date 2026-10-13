---
name: git-flujo-integrador
description: Usar al crear issues, milestones, ramas, commits o PRs en los repos del proyecto integrador (org manudiiez-um-programacion): convenciones, hook de commits y flujo de ramas.
---

# Flujo Git/GitHub del proyecto integrador

Aplica a los tres repos del proyecto (app KMP Android, servicio de catálogo y sincronización, servicio de turnos y reservas), en la organización `manudiiez-um-programacion`. Los tres tienen la misma configuración y siguen el mismo flujo.

Principio rector: **nada se trabaja sin issue**. Primero se planifica (milestones + issues), después se trabaja issue por issue, y el profesor revisa por milestone.

## 1. Configuración de los repos (hechos verificados)

- Ramas protegidas por el ruleset "main protection": `main`, `develop`, `testing`.
- Reglas sobre esas tres ramas:
  - Pull request obligatorio: no se puede pushear commits directo.
  - 1 aprobación requerida + review de Code Owners. `CODEOWNERS` = `* @dqmdz-um` (el profesor): todo PR hacia estas ramas necesita su aprobación. El autor no puede aprobarse.
  - Force push y borrado bloqueados. Bypass list vacía: aplica a todos.
- Cualquier otra rama (`milestone/*`, `feat/*`, etc.) **no está protegida**: se puede pushear y mergear sin aprobación.
- No hay status checks obligatorios, historial lineal, firma de commits ni restricción de nombres.
- Merge commit, squash y rebase permitidos. Auto-delete de ramas y auto-merge desactivados.
- Las palabras de cierre (`Closes #N`) solo cierran issues cuando el PR se mergea en la rama por defecto (`main`).
- Requisito de la cátedra: el historial debe mostrar la evolución del trabajo y las contribuciones del alumno. El profesor pidió que los issues existan **antes** de los PRs.

## 2. Modelo de ramas

```
feat/m1-12-...  ─┐
fix/m1-15-...   ─┼─ PR propio (sin aprobación) ─→ milestone/m1-...
docs/m1-18-...  ─┘                                      │
                                    PR + aprobación del profesor
                                                        ↓
                                                     develop ─→ testing ─→ main
                                                     (promociones por PR, con aprobación)
```

- **Rama de milestone**: agrupa todos los issues de un milestone. Sale de `develop`. Cuando el milestone está completo, se abre **un** PR `milestone/* → develop` para que el profesor lo revise.
- **Rama de issue**: una por issue. Sale de la rama de su milestone y vuelve a ella por PR.
- **Promociones** `develop → testing → main`: solo cuando el usuario lo pide.
- Nunca se commitea directo en una rama de milestone: solo recibe merges de ramas de issue y actualizaciones desde `develop`.

## 3. Convenciones de nombres

| Elemento | Formato | Ejemplo |
|---|---|---|
| Milestone (GitHub) | `MN - Nombre` | `M1 - Infraestructura base` |
| Rama de milestone | `milestone/mN-slug` | `milestone/m1-infraestructura-base` |
| Issue (título) | `tipo(scope): descripción en imperativo` | `feat(sync): aplicar cambios incrementales en orden` |
| Rama de issue | `tipo/mN-NN-slug` | `feat/m1-12-sync-incremental` |
| Commit | Conventional Commits (sección 5) | `feat(sync): aplicar cambios incrementales en orden` |
| PR issue → milestone | Mismo título que el issue | `feat(sync): aplicar cambios incrementales en orden` |
| PR milestone → develop | `MN - Nombre` | `M1 - Infraestructura base` |
| PR de promoción | `release: origen → destino (MN)` | `release: testing → main (M1)` |

- Tipos: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `build`, `perf`, `style`, `revert`.
- `scope`: módulo afectado, en minúsculas y sin acentos (`sync`, `catalogo`, `turnos`, `auth`, `kafka`, `redis`, `db`, `docker`, `ui`, `repo`).
- `slug`: minúsculas, sin acentos, palabras separadas por guiones, corto.
- Labels: uno por tipo (`feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`, `build`), aplicado a cada issue según su título.
- Excepción sin milestone (hotfix urgente): rama `fix/NN-slug` desde `develop` y PR directo a `develop`. Igual requiere issue.

## 4. Planificación: issues y milestones

Cuando el usuario pida planificar (crear issues y milestones):

1. Leer la documentación del proyecto (enunciado, referencia de integración) y el estado del repo.
2. Revisar lo que ya existe para no duplicar:
   ```bash
   gh api repos/{owner}/{repo}/milestones --jq '.[].title'
   gh issue list --state all --limit 200
   gh label list
   ```
3. Proponer al usuario una tabla: milestones (con objetivo y fecha si la hay) y sus issues (título convencional, label, criterios de aceptación resumidos). Issues chicos: cada uno terminable en pocos días. **Esperar aprobación antes de crear nada.**
4. Crear, en este orden:
   ```bash
   # labels
   gh label create feat --color 1D76DB --description "Nueva funcionalidad"
   # milestones (due_on opcional, formato ISO 8601)
   gh api repos/{owner}/{repo}/milestones -f title="M1 - Infraestructura base" -f description="..." -f due_on="2026-11-01T00:00:00Z"
   # issues
   gh issue create --title "feat(sync): ..." --body-file /tmp/issue.md --label feat --milestone "M1 - Infraestructura base"
   ```
5. Cada repo tiene sus propios issues. Para dependencias entre repos, referenciar `manudiiez-um-programacion/<repo>#N` en el cuerpo.

Plantilla del cuerpo del issue:

```markdown
## Contexto
Qué problema resuelve y por qué hace falta.

## Criterios de aceptación
- [ ] ...
- [ ] ...

## Notas técnicas
Decisiones, endpoints, tablas o eventos involucrados.

## Fuera de alcance
Lo que este issue no cubre.

## Dependencias
- #NN / manudiiez-um-programacion/<repo>#NN (si aplica)
```

## 5. Commits: Conventional Commits obligatorio

Formato:

```
tipo(scope): resumen en imperativo

Cuerpo OBLIGATORIO: qué se hizo y por qué. Decisiones relevantes,
no una repetición del diff. Líneas de ~72 caracteres.

Refs #NN
```

- Resumen: imperativo, en español, minúscula inicial, sin punto final, máximo 72 caracteres.
- Cuerpo obligatorio en todos los commits (salvo merges, que genera git).
- Footer: `Refs #NN` con el issue de la rama.
- Breaking change: `tipo(scope)!: ...` y footer `BREAKING CHANGE: descripción`.
- Commits atómicos: un cambio lógico por commit, que compile.
- Antes de commitear: revisar `git status` y `git diff --staged`; agregar archivos explícitamente, no `git add .` a ciegas. Verificar que no entren `.env`, builds, archivos del IDE ni secretos.
- Mantener las líneas de atribución que agrega la herramienta (`Co-Authored-By`); el hook las acepta.
- **Nunca** usar `git commit --no-verify` ni `-n`.

### Hook que bloquea commits inválidos

Cada repo versiona un hook `commit-msg` en `.githooks/` que rechaza cualquier commit que no cumpla el formato (título, longitud, línea en blanco, cuerpo). Funciona en macOS, Linux y Git Bash en Windows, sin dependencias.

Al empezar a trabajar en un repo, verificar:

```bash
test -x .githooks/commit-msg && git config core.hooksPath
```

- Si el hook existe pero `core.hooksPath` no es `.githooks`: ejecutar `git config core.hooksPath .githooks` (es configuración local, va una vez por clon).
- Si el hook no existe en el repo: proponer al usuario el issue `chore(repo): configurar hook de conventional commits` y agregarlo por el flujo normal (rama → PR). Archivos:

`.gitattributes` (evita que Windows rompa el script con CRLF):

```
.githooks/* text eol=lf
```

`.githooks/commit-msg` (con permiso de ejecución: `chmod +x .githooks/commit-msg` y `git update-index --chmod=+x .githooks/commit-msg`):

```sh
#!/bin/sh
# Valida que el mensaje de commit cumpla Conventional Commits con cuerpo obligatorio.
# Instalar una vez por clon:  git config core.hooksPath .githooks

MSG_FILE="$1"

# Quitar CR (Windows), comentarios y todo lo que sigue a la linea "scissors" de git commit -v
CLEAN=$(tr -d '\r' < "$MSG_FILE" | sed '/^# -\{24\} >8 -\{24\}$/,$d' | grep -v '^#')

HEADER=$(printf '%s\n' "$CLEAN" | sed -n '1p')

# Excepciones generadas por git
case "$HEADER" in
  "Merge "*|"Revert \""*|"fixup! "*|"squash! "*|"amend! "*) exit 0 ;;
esac

TYPES='feat|fix|docs|test|refactor|chore|ci|build|perf|style|revert'

fail() {
  echo "" >&2
  echo "ERROR commit rechazado: $1" >&2
  echo "" >&2
  echo "Formato requerido:" >&2
  echo "  tipo(scope): resumen en imperativo (max 72 caracteres, sin punto final)" >&2
  echo "  <linea en blanco>" >&2
  echo "  Cuerpo: que se hizo y por que (obligatorio)." >&2
  echo "  <linea en blanco>" >&2
  echo "  Refs #NN" >&2
  echo "" >&2
  echo "Tipos validos: $(echo $TYPES | tr '|' ' ')" >&2
  echo "Ejemplo:" >&2
  echo "  feat(sync): aplicar cambios incrementales en orden" >&2
  echo "" >&2
  echo "  La version local solo avanza al terminar la transaccion," >&2
  echo "  para evitar estados intermedios ante fallos." >&2
  echo "" >&2
  echo "  Refs #12" >&2
  echo "" >&2
  echo "Tu mensaje quedo guardado en .git/COMMIT_EDITMSG" >&2
  exit 1
}

[ -n "$HEADER" ] || fail "el mensaje esta vacio."

printf '%s\n' "$HEADER" | grep -Eq "^($TYPES)(\([a-z0-9._/-]+\))?!?: [^ ].*[^ .]$" \
  || fail "el titulo no respeta 'tipo(scope): resumen'."

LEN=$(printf '%s' "$HEADER" | wc -m | tr -d ' ')
[ "$LEN" -le 72 ] || fail "el titulo tiene $LEN caracteres (maximo 72)."

LINE2=$(printf '%s\n' "$CLEAN" | sed -n '2p')
[ -z "$LINE2" ] || fail "falta la linea en blanco entre el titulo y el cuerpo."

# El cuerpo debe tener al menos una linea que no sea trailer (Refs #N, Closes #N, Co-Authored-By:, etc.)
BODY_TEXT=$(printf '%s\n' "$CLEAN" | sed '1,2d' \
  | grep -Ev '^[[:space:]]*$' \
  | grep -Ev '^[A-Za-z][A-Za-z-]*: ' \
  | grep -Ev '^(Refs|Closes|Fixes|Resolves|Part of) #[0-9]+' \
  | grep -Ev '^BREAKING CHANGE: ')
[ -n "$BODY_TEXT" ] || fail "falta el cuerpo explicando que se hizo y por que."

exit 0
```

Si el hook rechaza un commit, corregir el mensaje y reintentar; nunca saltearlo.

## 6. Flujo de trabajo paso a paso

### 6.1 Empezar un milestone

Si `develop` no existe en el remoto, detenerse y avisar al usuario (no crearla por cuenta propia).

```bash
git switch develop && git pull origin develop
git switch -c milestone/m1-infraestructura-base
git push -u origin milestone/m1-infraestructura-base
```

### 6.2 Empezar un issue

Requisito: el issue existe y pertenece al milestone. Si no existe, crearlo primero: mostrar el borrador al usuario y crearlo solo con su confirmación.

```bash
git switch milestone/m1-infraestructura-base && git pull
# crea la rama vinculada al issue (aparece en "Development" del issue)
gh issue develop 12 --base milestone/m1-infraestructura-base --name feat/m1-12-sync-incremental --checkout
```

Si `gh issue develop` no está disponible: `git switch -c feat/m1-12-sync-incremental`.

### 6.3 Trabajar

Commits chicos con el formato de la sección 5 y `Refs #12`. Pushear la rama seguido (sirve de backup):

```bash
git push -u origin feat/m1-12-sync-incremental
```

Para traer cambios de la rama de milestone, preferir merge (`git fetch origin && git merge origin/milestone/m1-...`). Si se hace rebase de una rama de issue ya pusheada, pushear solo con `--force-with-lease` y solo a esa rama propia.

### 6.4 Integrar el issue al milestone

Cuando se cumplen los criterios de aceptación del issue. Mostrar al usuario el resumen y pedir confirmación antes de crear y mergear el PR.

```bash
gh pr create --base milestone/m1-infraestructura-base \
  --title "feat(sync): aplicar cambios incrementales en orden" \
  --body-file /tmp/pr.md
gh pr merge --merge --delete-branch
```

Cuerpo del PR issue → milestone:

```markdown
## Qué cambia
- ...

## Por qué
...

## Cómo probarlo
1. ...

Refs #12
```

Se usa `Refs`, no `Closes`: el issue se cierra recién al llegar a `main`. Merge con **merge commit** para conservar los commits del issue.

### 6.5 Cerrar el milestone: PR a develop

Cuando todos los issues del milestone están integrados en su rama:

```bash
git switch milestone/m1-infraestructura-base && git pull
git fetch origin && git merge origin/develop     # resolver conflictos acá, nunca en develop
git push
gh issue list --milestone "M1 - Infraestructura base" --state all --json number,title
gh pr create --base develop --title "M1 - Infraestructura base" --body-file /tmp/pr.md
```

Cuerpo del PR milestone → develop:

```markdown
## Resumen del milestone
Qué aporta este milestone en conjunto.

## Issues incluidos
- Refs #12 — feat(sync): aplicar cambios incrementales en orden (PR #20)
- Refs #13 — ...

## Cómo probarlo
1. ...

## Notas / riesgos
...
```

- Listar cada issue con su PR interno, para que el profesor pueda revisar issue por issue.
- Si el milestone no está terminado pero conviene mostrar avance, abrir el PR con `--draft`.
- Si después de la aprobación se pushean cambios importantes, avisar al usuario para que pida un nuevo review (las aprobaciones no se descartan solas).
- El profesor recibe el pedido de review automáticamente (CODEOWNERS). Después de su aprobación, mergear con **merge commit** (nunca squash) y borrar la rama de milestone.

### 6.6 Promociones develop → testing → main

Solo cuando el usuario lo pida.

```bash
gh pr create --base testing --head develop --title "release: develop → testing (M1)" --body-file /tmp/pr.md
gh pr create --base main --head testing --title "release: testing → main (M1)" --body-file /tmp/pr.md
```

- Siempre **merge commit**, nunca squash ni rebase (si no, las ramas divergen y los próximos PRs dan conflictos).
- **El PR hacia `main` debe cerrar los issues.** Incluir una línea `Closes #N` por cada issue que llega a `main` (una palabra de cierre por issue; `Closes #12, #13` no cierra el segundo):
  ```markdown
  ## Issues que se cierran
  Closes #12
  Closes #13
  Closes #15
  ```
  Para armar la lista, cruzar los issues de los milestones incluidos con los `Refs #N` de los commits que llegan:
  ```bash
  git fetch origin
  git log origin/main..origin/testing --format=%B | grep -Eo '#[0-9]+' | sort -u
  ```
- PR hacia `testing`: listar los issues con `Refs #N`.
- Después del merge a `main`: verificar que los issues quedaron cerrados y, con confirmación del usuario, cerrar el milestone en GitHub:
  ```bash
  gh api repos/{owner}/{repo}/milestones --jq '.[] | "\(.number) \(.title)"'
  gh api -X PATCH repos/{owner}/{repo}/milestones/<numero> -f state=closed
  ```

## 7. Prohibido

- Pushear o commitear directo a `main`, `develop` o `testing`, o hacer force push / borrar esas ramas.
- Commitear directo en una rama `milestone/*` (solo recibe merges).
- Crear una rama de trabajo o un PR sin issue asociado.
- `git commit --no-verify` / `-n`, o desactivar el hook.
- Modificar el ruleset, `CODEOWNERS` o la configuración del repo.
- Aprobar PRs, o mergear PRs hacia ramas protegidas. Los PRs hacia `milestone/*` se mergean solo con confirmación del usuario.
- Squash en PRs de milestone o de promoción.
- Commitear secretos: JWT técnico de la cátedra, `redisPassword`, credenciales, tokens, URLs privadas de Redis/Kafka/API. Van en `.env` (en `.gitignore`); se versiona solo `.env.example`.
- Abrir PRs de promoción sin pedido explícito del usuario.

## 8. Si algo falla

- **Push rechazado** ("protected branch" o "Changes must be made through a pull request"): se intentó pushear a una rama protegida. Mover los commits a una rama nueva con `git switch -c tipo/mN-NN-slug` y pushear esa. Para limpiar la rama protegida local (`git reset --hard origin/<rama>`), pedir confirmación antes.
- **Commit en la rama equivocada** (por ejemplo, en `milestone/*`): mismo procedimiento, crear la rama de issue desde ahí y limpiar la otra con confirmación.
- **Se commiteó un secreto**: avisar al usuario de inmediato. Borrarlo en otro commit no alcanza; hay que rotarlo (el JWT técnico se reemplaza creando otra cuenta técnica, según la documentación de integración).
- **Conflictos**: resolverlos en la rama de issue o de milestone, nunca en las protegidas.
- **Issue que resultó más grande de lo previsto**: proponer partirlo en issues nuevos del mismo milestone antes de seguir.

## 9. Pendiente de confirmar con el profesor

- Para qué usa `testing` y quién abre los PRs de promoción (`develop → testing → main`). Hasta confirmarlo, el agente solo abre promociones si el usuario lo pide.