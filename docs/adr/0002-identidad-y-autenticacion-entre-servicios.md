# ADR 0002 — Identidad del usuario final y autenticación entre servicios

- **Estado:** aceptada
- **Fecha:** 2026-10-26
- **Issue:** #59
- **Decisiones del proyecto que cierra:** D1 (dónde viven el registro y el
  login), D4 (JWT técnico interno o propagado), D5 (vía de la app KMP a la
  búsqueda)

## Contexto

El proyecto tiene tres repos: la app KMP, este servicio de catálogo y
sincronización, y el servicio de turnos y reservas. Las tres decisiones que
cierra este ADR son transversales a los tres y están encadenadas, así que se
acuerdan juntas: D5 (por dónde entra KMP) obliga a que este servicio valide el
token que emite el otro backend, que es D1; y D1 define qué credencial queda
disponible para las llamadas servidor a servidor, que es D4.

### Lo que la cátedra ya fijó

La referencia de integración §2 define **dos clases de identidad que no se
mezclan**: la *cuenta técnica del proyecto*, creada una vez con Postman contra
el servicio central y compartida por los dos backends (§4: "los dos servicios
backend del alumno usan la misma cuenta técnica y el mismo JWT de cátedra",
vigencia de un año); y el *usuario final*, que se registra desde KMP contra los
backends del alumno. El JWT técnico de cátedra nunca debe enviarse a KMP, y el
servicio central no conoce las cuentas finales.

El enunciado §3.2 fija el modelo de usuario: compatible con el de JHipster
(`login`, `password`, `firstName`, `lastName`, `email`, `imageUrl` opcional,
`langKey`), contraseña hasheada, usuario activo inmediatamente después del
registro, e ids internos, activación, autoridades y auditoría administrados por
el backend. §9 exige emitir un JWT al autenticar, validarlo en toda API
protegida, usar y validar JWT entre los dos backends, autorizar cada operación
según la identidad autenticada, impedir que un usuario toque procesos y reservas
de otro, externalizar secretos y configurar CORS según los clientes permitidos.

### Lo que la cátedra dejó abierto a propósito

- §3.2 dice que el usuario se registra "contra el backend desarrollado por el
  alumno", en singular y sin aclarar **cuál de los dos**.
- §9, textual: "El alumno deberá definir si la comunicación entre servicios
  propaga el JWT del usuario o utiliza un JWT técnico, y justificar como
  preserva la identidad, la autorización y la trazabilidad necesarias".
- §10 y §19 de la referencia listan entre las decisiones del alumno los
  "contratos entre sus dos servicios", el "mecanismo de autenticación entre
  servicios", los "endpoints y DTO entre sus propios servicios" y el
  "almacenamiento seguro de secretos".
- §4.1 pide que los filtros de búsqueda "deberán poder utilizarse desde la
  interfaz KMP", sin decir por qué vía.

Ninguna de las tres está respondida en la documentación de la cátedra: son
decisiones evaluadas por su justificación (§12).

### Lo que este servicio expone

Tres APIs propias, con tres audiencias distintas: la **búsqueda de
profesionales** con filtros (#15, #16), que consume la app KMP; la **agenda
vigente** (#18), que consume el servicio de turnos servidor a servidor; y el
**estado de sincronización** (#28), que es operativo. Las tres van protegidas
con JWT (§9). Sin estas decisiones, #17 y #18 no se pueden escribir.

## Decisión

### 1. La identidad del usuario final vive en el servicio de turnos (D1)

El **servicio de turnos y reservas** es dueño exclusivo de los usuarios finales:
la tabla, el hasheo de contraseñas, el registro, el login y la **emisión** del
JWT de usuario. Implementa el modelo compatible con JHipster de §3.2.

Este servicio **no** tiene tabla de usuarios, ni endpoints de registro o login,
ni hashea contraseñas: solo **valida** el JWT emitido allá. De cada token
necesita el `sub` (identificador estable del usuario) para los logs y nada más:
la búsqueda de catálogo no tiene propiedad por usuario, así que no autoriza por
identidad, solo exige estar autenticado.

El fundamento es que el dueño de la propiedad por usuario sea el que autentica:
§4.2 obliga a turnos a asociar cada proceso y cada reserva al usuario final que
los inició, y §2 de la referencia le obliga a enviar en `externalPatientId` "un
identificador estable del usuario dentro de la aplicación". Las tablas de este
servicio, en cambio, son una copia del catálogo de la cátedra: no hay una sola
fila propiedad de un usuario. Poner la autenticación acá separaría la identidad
de la única autorización que §9 exige ligar a ella.

### 2. La firma del JWT de usuario se valida con un secreto compartido HS256 (D1)

Turnos firma el JWT de usuario con HMAC-SHA256 y este servicio lo valida con el
**mismo secreto**, presente en el `.env` de los dos servicios como `JWT_USER_SECRET`
(mínimo 256 bits). El secreto nunca se versiona: `.env.example` lleva solo la
forma, y `.env` está en `.gitignore` (§9, §19).

Se valida firma, vigencia (`exp`) y emisor (`iss`, en `JWT_USER_ISSUER`) de todo
token recibido.

**Limitación aceptada y declarada**, que §9 pide documentar: con un secreto
simétrico compartido, este servicio es técnicamente capaz de *falsificar* un
token de usuario, no solo de verificarlo. Se acepta porque los dos backends son
del mismo proyecto, se despliegan juntos y ninguno es un tercero no confiable;
el riesgo real es que una filtración del secreto comprometa a los dos servicios
a la vez. El camino de mejora, si hiciera falta, es RS256 con la clave privada
solo en turnos y la pública en el `.env` de este servicio: es un cambio de
configuración del decodificador, no de código de dominio.

### 3. Entre los dos servicios se usa un JWT técnico interno propio (D4)

La API de agenda vigente (#18) **no** acepta el JWT del usuario final. El
servicio de turnos la llama con un **JWT técnico interno del proyecto**, firmado
con un secreto **distinto** (`JWT_INTERNAL_SECRET`), TTL corto, y un claim de
autoridad `SERVICE_TURNOS` que este servicio exige en las rutas internas.

Que los dos tipos de token se firmen con secretos distintos es lo que hace
cumplir el requisito de §9 de que un token no sirva para los endpoints de la
otra identidad: la separación es **criptográfica**, no una comprobación de
claims que se pueda olvidar. Un token de usuario presentado en la agenda falla
en la verificación de firma, y un token técnico interno presentado en la
búsqueda también.

Este JWT técnico interno **no es** el JWT técnico de la cátedra. Ese último se
usa únicamente como credencial de **salida** del slice `integration` hacia el
servicio central, nunca se acepta como credencial de entrada de este servicio y
nunca llega a la app KMP (§3.2, §2 de la referencia).

**Por qué técnico y no propagado.** El argumento decisivo es que turnos tiene
flujos **sin usuario presente** en los que igual necesita el catálogo vigente:
vencimiento de bloqueos temporales, recuperación de procesos pendientes o
interrumpidos (§4.2, §8) y consulta de reservas con la cuenta técnica. En esos
momentos no hay ningún JWT de usuario para reenviar. A eso se suma que la
consulta de agenda no tiene propiedad por usuario: la agenda de un profesional
es la misma para cualquiera, así que un token de usuario no aportaría ninguna
decisión de autorización que este servicio pudiera tomar.

**Cómo se preserva la trazabilidad**, que es lo que §9 pide justificar junto con
la identidad y la autorización: turnos propaga un header `X-Correlation-Id` por
llamada y registra de su lado qué usuario final originó la operación; este
servicio registra el servicio llamante y esa correlación. El rastro
usuario → operación → consulta de catálogo se reconstruye cruzando los logs de
los dos servicios, sin que la identidad del usuario tenga que viajar en una
credencial que no se va a usar para autorizar nada.

### 4. La app KMP consulta la búsqueda directamente a este servicio (D5)

KMP mantiene **dos URLs base**: las búsquedas y filtros de profesionales van a
este servicio con el JWT de usuario; el registro, el login y todo lo de reservas
van al servicio de turnos. CORS se restringe a los orígenes de
`CORS_ALLOWED_ORIGINS`, nunca `*` (§9).

El fundamento es §4.1: este servicio es "la única fuente local vigente para
consultar profesionales y sus reglas de agenda". Un proxy en turnos duplicaría
DTOs, filtros y paginado, agregaría un salto de red y acercaría ese servicio a
la prohibición de §3 de "duplicar en el servicio de turnos una réplica del
catálogo para búsquedas", sin aportar nada: dos URLs base en un cliente HTTP es
configuración, no complejidad.

### 5. Resumen operativo de las identidades en este servicio

| Rutas | Credencial exigida | Identidad |
| --- | --- | --- |
| `/actuator/health`, y OpenAPI solo en perfil `local` | ninguna | pública |
| Búsqueda y filtros de profesionales (#15, #16) | JWT de usuario final, firmado con `JWT_USER_SECRET` | usuario final, emitido por turnos |
| Agenda vigente (#18) | JWT técnico interno con autoridad `SERVICE_TURNOS`, firmado con `JWT_INTERNAL_SECRET` | servicio de turnos |
| Estado de sincronización (#28) | JWT técnico interno | operación del proyecto |
| Cualquier otra | token exigido; sin token, 401 en `application/problem+json` | — |

Tres secretos distintos, los tres externalizados y ninguno en el repo: el del
JWT de usuario (compartido con turnos), el del JWT técnico interno (compartido
con turnos) y las credenciales de la cuenta técnica de cátedra (propias de este
servicio, nunca compartidas con KMP).

El JWT técnico de cátedra no autentica a nadie contra este servicio.

## Consecuencias

### A favor

- #17 y #18 quedan implementables: dos cadenas de filtros de Spring Security
  separadas por patrón de ruta, cada una con su decodificador y su secreto.
- Este servicio no tiene usuarios, ni hashing, ni registro, ni login: menos
  superficie de ataque, y cero duplicación del modelo de §3.2 entre los dos
  repos.
- Los flujos de turnos sin usuario presente (vencimientos, recuperación de
  procesos interrumpidos) pueden consultar el catálogo, que es justo cuando más
  lo necesitan.
- El aislamiento por usuario que exige §9 queda donde están los datos por
  usuario, en un solo servicio y sin coordinación entre repos.
- La separación entre las dos identidades es criptográfica: no depende de
  recordar comprobar un claim.
- La app KMP habla con cada servicio para lo que cada uno es dueño, sin un proxy
  intermedio que haya que mantener sincronizado con los filtros.

### En contra

- **Tres secretos para gestionar y rotar**, dos de ellos compartidos entre dos
  repos. Rotar el del JWT de usuario obliga a desplegar los dos servicios
  juntos; mientras la rotación esté a medias, los tokens vigentes fallan.
- El secreto simétrico compartido permite que este servicio falsifique tokens de
  usuario (limitación de la sección 2). Es una decisión consciente, no un
  descuido, y queda declarada en la documentación de seguridad (#35).
- La trazabilidad del usuario final en las llamadas servidor a servidor es
  **indirecta**: si se pierde el `X-Correlation-Id`, el rastro se corta y hay
  que reconstruirlo por timestamps.
- KMP configura y mantiene dos URLs base, y este servicio una lista de orígenes
  CORS que hay que actualizar cuando cambie el entorno de la app.
- Las tres decisiones **acoplan los tres repos**: cambiarlas más adelante toca
  los tres a la vez. Es el precio de cerrarlas; dejarlas abiertas bloqueaba
  #15, #16, #17, #18, #32 y #33.

## Alternativas consideradas

### Registro y login en este servicio de catálogo (D1)

Habría concentrado toda la emisión de tokens acá y dejado a turnos solo
validando. Se descarta porque este servicio no es dueño de ningún dato por
usuario: sus tablas son una copia del catálogo de la cátedra, y la única
autorización por identidad que exige §9 —que un usuario no toque procesos ni
reservas de otro— se aplica íntegramente en turnos. Además turnos quedaría
dependiendo de este servicio para que un usuario pueda hacer login, es decir,
una caída de la sincronización del catálogo podría impedir iniciar sesión, que
es un acoplamiento sin ninguna justificación funcional.

### Clave pública RS256 por variable de entorno, o JWKS expuesto por turnos (D1)

RS256 con la privada solo en turnos elimina la limitación de la sección 2: este
servicio no podría falsificar tokens. JWKS es además lo más estándar y permite
rotar claves sin redesplegar. Se descartan **por ahora**: RS256 obliga a generar
y distribuir un par de claves por entorno, y JWKS agrega acoplamiento en tiempo
de ejecución —este servicio tendría que alcanzar un endpoint de turnos al
arrancar y al refrescar— sin beneficio real a la escala de dos backends que se
despliegan juntos. RS256 queda registrado como camino de mejora, y es un cambio
de configuración del decodificador.

### Propagar el JWT del usuario final entre los dos servicios (D4)

Es la opción con una sola identidad en juego y un solo secreto, y preserva la
identidad del usuario de punta a punta sin headers de correlación. Se descarta
porque turnos tiene operaciones legítimas sin usuario presente (vencimiento de
holds, recuperación de procesos interrumpidos, consulta de reservas con la
cuenta técnica, §4.2 y §8) que igual necesitan la agenda vigente: con token
propagado esas operaciones se quedan sin credencial y habría que agregar de
todos modos un camino técnico, quedándose con lo peor de las dos opciones.
Tampoco aporta autorización: la agenda de un profesional no es propiedad de
ningún usuario.

### Reusar el JWT técnico de la cátedra como credencial entre los dos servicios (D4)

Tentador porque los dos backends ya lo tienen: §4 de la referencia dice que
"usan la misma cuenta técnica y el mismo JWT de cátedra". Se descarta por tres
razones: §3.2 establece que la cuenta de usuario final y la cuenta técnica de
integración "representan identidades diferentes"; ese token tiene un año de
vigencia y es la credencial más sensible del proyecto, así que aceptarlo como
credencial de entrada propia significaría que cualquier filtración en el borde
HTTP compromete la integración entera con la cátedra; y §9 pide "evitar confiar
en identificadores de usuario o de integración técnica enviados libremente por
el cliente". El JWT de cátedra se usa solo hacia afuera.

### La app KMP accede a la búsqueda a través del servicio de turnos (D5)

KMP hablaría con un único backend, lo que simplifica su configuración y permite
un punto único de autenticación. Se descarta porque turnos tendría que duplicar
los DTOs de búsqueda, los filtros y el paginado de este servicio y mantenerlos
sincronizados, agrega un salto de red en la pantalla más usada de la app, y
roza la prohibición de §3 de duplicar el catálogo en turnos para búsquedas. §4.1
señala a este servicio como la única fuente local vigente de esa consulta.

## Pendiente de replicar en los otros repos

Este ADR es la versión de este repo. Cuando se planifiquen los otros dos, hay
que dejar registrada la misma decisión del lado que les toca:

- `manudiiez-um-programacion/turnos-reservas-service`: emisión del JWT de
  usuario y modelo de §3.2, emisión del JWT técnico interno, y el
  `X-Correlation-Id` en el cliente de la agenda.
- `manudiiez-um-programacion/turnos-app-android`: dos URLs base y a qué
  servicio va cada llamada.

## Referencias

- Enunciado `docs/catedra/PROJECT_STATEMENT-v1.md`: §3, §3.2, §4.1, §4.2, §8, §9, §10, §12
- Referencia de integración `docs/catedra/INTEGRATION_REFERENCE-v2.md`: §2, §2.1, §4, §19
- ADR `0001-arquitectura-hexagonal.md` (slices `shared` e `integration`)
- Issues que destraba: #15, #16, #17, #18, #20, #28, #32, #33
