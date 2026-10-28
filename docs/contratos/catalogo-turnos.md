# Contrato REST: catálogo → servicio de turnos

Lo que el servicio de **catálogo y sincronización** le expone al servicio de
**turnos y reservas**. §12 del enunciado exige documentar los contratos entre
los dos servicios, incluyendo operaciones, DTO, autenticación y errores.

- **Versión del contrato:** `1.0.0`
- **Documento OpenAPI:** [`openapi.yaml`](openapi.yaml), generado por el propio
  servicio y verificado contra lo que realmente responde
- **Decisión de autenticación:** [ADR 0002](../adr/0002-identidad-y-autenticacion-entre-servicios.md)

## 1. Por qué existe este contrato

§4.1 hace de este servicio "la única fuente local vigente para consultar
profesionales y sus reglas de agenda". §4.2 obliga al servicio de turnos a
"obtener del servicio de catálogo la información vigente que necesite" antes de
iniciar una operación nueva.

Y §3 cierra las otras vías. El servicio de turnos **no puede**:

- acceder a las tablas, repositorios o estructuras internas de este servicio;
- usar una base de datos compartida como mecanismo de integración;
- duplicar una réplica del catálogo para búsquedas o reservas nuevas;
- reemplazar este contrato por acoplamiento a código interno.

**Esta API es la única vía.** No depende solo de la disciplina: el rol de base
`catalogo` es dueño únicamente de su base lógica y no tiene permisos cruzados,
así que no existe una credencial con la que el servicio de turnos pueda leer
estas tablas.

Lo que **sí** puede hacer turnos, y este contrato habilita: conservar en una
reserva los datos descriptivos históricos del profesional, siempre que no los
use como catálogo vigente (§4.2).

## 2. Qué no está en este contrato

- **Disponibilidad, ocupaciones, bloqueos y reservas.** Son del servicio de
  turnos. Este contrato devuelve *reglas de agenda*: responde "tiene agenda",
  no "tiene cupo libre".
- **Búsqueda y filtros de profesionales.** Los consume la app KMP con el JWT
  del usuario final, no el servicio de turnos (decisión D5 del ADR 0002).
- **Escritura de cualquier tipo.** La copia local se escribe desde la
  sincronización con la cátedra, que es la fuente autoritativa.

## 3. Autenticación

| | |
| --- | --- |
| Credencial | JWT técnico interno del proyecto |
| Cabecera | `Authorization: Bearer <jwt>` |
| Algoritmo | HS256, secreto compartido (`JWT_INTERNAL_SECRET`) |
| Emisor esperado | claim `iss` igual a `JWT_INTERNAL_ISSUER` |
| Autoridad requerida | `SERVICE_TURNOS`, en el claim `authorities` |
| Vigencia | se valida `exp`; el TTL lo decide quien emite |

Tres aclaraciones que evitan los errores más probables:

1. **No es el JWT técnico de la cátedra.** Ese se usa solo como credencial de
   *salida* hacia el servicio central y no se acepta como credencial de entrada
   acá: no está firmado con este secreto, así que un intento devuelve 401.
2. **No sirve el JWT del usuario final.** Está firmado con otro secreto
   (`JWT_USER_SECRET`), así que falla la verificación de firma y devuelve
   **401**, no 403. La separación entre las dos identidades es criptográfica, no
   una comprobación de claims.
3. **El secreto tiene que ser idéntico en los dos servicios.** Si no coincide,
   todas las llamadas dan 401 con `code` `AUTH_TOKEN_INVALID`.

Se recomienda propagar un header `X-Correlation-Id` por llamada: este servicio
lo registra junto al servicio llamante, y es lo que permite reconstruir el
rastro usuario → operación → consulta de catálogo cruzando los logs de los dos
servicios (§9 pide preservar la trazabilidad).

## 4. Operación: agenda vigente de un profesional

```http
GET /api/internal/professionals/{professionalId}/agenda
Authorization: Bearer <jwt-tecnico-interno>
```

**Sin cuerpo de request.** El único parámetro es de ruta:

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `professionalId` | `integer` (int64) | Id del profesional **en el catálogo de la cátedra**, no un id local |

### Respuesta `200 OK`

```json
{
  "professional": {
    "id": 42,
    "categoryId": 7,
    "firstName": "Ana",
    "lastName": "Gomez",
    "enabled": true
  },
  "schedules": [
    {
      "id": 315,
      "dayOfWeek": "MONDAY",
      "startTime": "09:00",
      "endTime": "13:00",
      "slotDurationMinutes": 30
    }
  ],
  "catalogVersion": {
    "version": 128,
    "appliedAt": "2026-10-27T18:30:00Z",
    "source": "SNAPSHOT"
  }
}
```

#### `professional` (`AgendaProfessional`)

| Campo | Tipo | Notas |
| --- | --- | --- |
| `id` | `integer` (int64) | Id del catálogo de la cátedra |
| `categoryId` | `integer` (int64) | Categoría profesional |
| `firstName` | `string` | |
| `lastName` | `string` | |
| `enabled` | `boolean` | Siempre `true`: un profesional deshabilitado devuelve 409, no agenda |

No incluye `createdAt` ni `updatedAt`: son del registro local —cuándo esta copia
escribió la fila— y no significan nada del otro lado. Para saber sobre qué copia
se está trabajando está `catalogVersion`.

#### `schedules` (lista de `AgendaWeeklySchedule`)

| Campo | Tipo | Notas |
| --- | --- | --- |
| `id` | `integer` (int64) | Id del horario en el catálogo de la cátedra |
| `dayOfWeek` | `string` | `MONDAY`…`SUNDAY`, como en el contrato de la cátedra |
| `startTime` | `string` | `HH:mm` |
| `endTime` | `string` | `HH:mm` |
| `slotDurationMinutes` | `integer` | Duración de cada turno |

Garantías sobre esta lista:

- **Solo horarios habilitados.** La copia local guarda también los
  deshabilitados, porque el snapshot los trae, pero no se publican como agenda
  vigente.
- **Orden estable:** por día de la semana y, dentro del día, por hora de inicio.
  El orden es parte del contrato.
- **Puede venir vacía.** Un profesional habilitado sin horarios es un `200` con
  `schedules: []`, no un error.

#### `catalogVersion` (`AppliedCatalogVersion`)

| Campo | Tipo | Notas |
| --- | --- | --- |
| `version` | `integer` (int64) | Versión del catálogo de la cátedra |
| `appliedAt` | `string` (date-time) | Cuándo esta copia terminó de aplicarla, en UTC |
| `source` | `string` | `SNAPSHOT` o `INCREMENTAL` |

## 5. Errores

Todos en `application/problem+json`, con la forma de §13 de la referencia de
integración:

```json
{
  "type": "https://www.jhipster.tech/problem/problem-with-message",
  "title": "Not Found",
  "status": 404,
  "detail": "No hay un profesional con id 777 en la copia local del catalogo",
  "message": "error.http.404",
  "path": "/api/internal/professionals/777/agenda",
  "code": "PROFESSIONAL_NOT_FOUND"
}
```

**Decidir por `status` y `code`, nunca por el texto de `detail`.** El `code` es
parte del contrato y `detail` no: cambiar un mensaje no mueve la versión del
contrato, renombrar un `code` sí.

| HTTP | `code` | Condición | Qué debería hacer turnos |
| --- | --- | --- | --- |
| 401 | `AUTH_TOKEN_MISSING` | No vino la cabecera `Authorization` | Revisar el cliente |
| 401 | `AUTH_TOKEN_INVALID` | Token vencido, firmado con otra clave, de otro emisor o mal formado | Revisar que `JWT_INTERNAL_SECRET` coincida en los dos servicios |
| 403 | `AUTH_FORBIDDEN` | Token válido sin la autoridad `SERVICE_TURNOS` | Revisar el claim `authorities` al emitir |
| 404 | `PROFESSIONAL_NOT_FOUND` | El profesional no está en la copia local | Rechazar la operación: no existe en el catálogo vigente |
| 409 | `PROFESSIONAL_DISABLED` | Está en la copia local pero deshabilitado | **Rechazar una operación nueva**, pero puede seguir mostrando reservas ya hechas con sus datos históricos (§4.2) |
| 503 | `CATALOG_NOT_SYNCHRONIZED` | Todavía no hay una versión de catálogo aplicada | Reintentar con backoff: es un estado transitorio de este servicio |
| 500 | sin garantía de `code` | Error interno no previsto | Reintentar; §13.1 no garantiza `code` en los 500 |

### Por qué 404 y 409 son distintos

Es lo que le permite a turnos decidir. Con un profesional **inexistente** no
hay nada que hacer. Con uno **deshabilitado**, tiene que rechazar una operación
nueva pero las reservas que ya existen siguen siendo válidas y mostrables. Un
único 404 para los dos casos lo obligaría a adivinar.

### Por qué 409 y no el 400 de §13.1

La matriz de la cátedra usa `400 PROFESSIONAL_DISABLED`, pero allá la condición
aparece al **crear un hold**, donde un profesional deshabilitado hace inválido
el pedido. Acá es una **consulta correcta** sobre un recurso que existe y cuyo
estado no permite la respuesta, que es lo que significa 409. El `code` se
mantiene igual para que un cliente que ya lo interpreta no necesite un caso
especial.

## 6. Consistencia: qué pasa si turnos consulta durante una sincronización

Esta es la pregunta que importa, porque la copia local se reescribe mientras el
otro servicio la consulta.

### Lo que está garantizado

1. **La respuesta nunca mezcla versiones.** La consulta resuelve las tres
   lecturas —versión aplicada, profesional, horarios— en una sola transacción
   de solo lectura con aislamiento **REPEATABLE READ**. PostgreSQL toma una
   única instantánea en la primera lectura y las tres ven el mismo estado.

   > No alcanzaba con una transacción común: el aislamiento por defecto de
   > PostgreSQL es READ COMMITTED y ahí **cada sentencia** toma su propia
   > instantánea, así que un commit de la sincronización entre la lectura de la
   > versión y la de los horarios habría producido exactamente la mezcla que
   > este contrato promete evitar.

2. **Nunca se ve una sincronización a medias.** El snapshot completo se aplica
   en una sola unidad de trabajo y la versión local avanza únicamente cuando
   terminó bien. Quien lea verá el estado completo anterior o el completo nuevo,
   nunca uno parcial.

3. **Los datos corresponden exactamente a la `catalogVersion` que se informa.**
   Es la garantía que se deriva de las dos anteriores, y es la única que turnos
   necesita para razonar.

4. **La consulta no se bloquea ni espera.** REPEATABLE READ en PostgreSQL se
   implementa con MVCC, sin bloqueos de lectura: una sincronización en curso no
   agrega latencia a esta llamada.

### Lo que NO está garantizado

- **Que la versión sea la última disponible en la cátedra.** Esta copia puede
  estar algunas versiones atrás. `catalogVersion.version` dice exactamente
  dónde está.
- **Que dos llamadas consecutivas vean la misma versión.** Entre una y otra la
  sincronización puede avanzar. Turnos lo detecta comparando
  `catalogVersion.version`, sin preguntar nada.
- **Que un profesional siga existiendo o habilitado.** Una sincronización puede
  eliminarlo o deshabilitarlo entre dos llamadas, y la segunda devolverá 404 o
  409.

### Recomendación para el servicio de turnos

Guardar la `catalogVersion` junto a cada proceso de reserva. Si cambió entre la
lectura de la agenda y la confirmación, volver a leer la agenda antes de
confirmar: es la forma de no confirmar un turno contra reglas que ya no están
vigentes.

## 7. Criterio de versionado

La versión del documento OpenAPI (`info.version`) es la del **contrato**, no la
del artefacto desplegado.

**No mueve la versión:**

- agregar un campo opcional a una respuesta;
- cambiar el texto de un `detail`, un `summary` o una descripción;
- agregar una operación nueva.

**Sube la minor** cuando se agrega algo que el otro lado puede querer usar pero
no necesita: un campo nuevo, un `code` nuevo para una condición que antes caía
en otro.

**Sube la major** cuando algo que el otro lado ya usa deja de funcionar como
antes: quitar o renombrar un campo, renombrar un `code`, cambiar el `status` de
una condición existente, cambiar el formato de un valor, o volver obligatorio un
parámetro que era opcional.

Cualquier cambio de major se coordina con el repo de turnos **antes** de
mergearlo.

### Cómo se mantiene honesto el `openapi.yaml`

El archivo versionado no se edita a mano. Lo genera el propio servicio y una
prueba falla si el archivo y lo que el servicio publica dejan de coincidir:

```bash
# verificar (corre en ./mvnw verify)
./mvnw test -Dtest=ContratoOpenApiVersionadoTest

# regenerar, cuando el cambio es intencional
./mvnw test -Dtest=ContratoOpenApiVersionadoTest -Dcontrato.exportar=true
```

El diff de esa regeneración **es** el cambio de contrato, y es lo que hay que
mirar para decidir si mueve la versión.

## 8. Trazabilidad con el repo de turnos

| Lado | Issue |
| --- | --- |
| Catálogo (este repo) | #18 (endpoint), #20 (este documento), #17 (autenticación) |
| Turnos | `manudiiez-um-programacion/turnos-reservas-service` — **pendiente** |

> **Pendiente:** el repo `turnos-reservas-service` todavía no tiene milestones
> ni issues, así que no hay número al que apuntar. Cuando se lo planifique, el
> issue que consuma este contrato tiene que referenciar `#18` y `#20` de este
> repo, y hay que completar la fila de arriba para que el contrato quede trazado
> en los dos lados.
>
> Ojo con el nombre: el repositorio es `turnos-reservas-service`, no
> `turnos-reservas`.
