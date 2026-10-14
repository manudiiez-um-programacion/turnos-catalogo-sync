-- Copia local del catalogo de la catedra.
--
-- Las tres tablas reflejan las colecciones del snapshot (§7 de la
-- referencia de integracion): professionalCategories, professionals y
-- weeklySchedules. Los campos son los del contrato, sin agregados ni
-- renombres de significado: solo se pasa de camelCase a snake_case.
--
-- El id de la catedra es la clave primaria, no una clave subrogada. Es la
-- identidad real de la entidad en el sistema de origen, es la que traen
-- tanto el snapshot como los cambios incrementales, y usarla directo hace
-- que aplicar una version sea un upsert por id en lugar de una busqueda
-- previa. No hay riesgo de colision: este servicio no crea entidades
-- propias de catalogo, solo copia las de la catedra.
--
-- created_at y updated_at son DATOS de la catedra, no auditoria nuestra:
-- vienen en el contrato y se guardan tal como llegan, en UTC.

-- Busqueda parcial por nombre, insensible a mayusculas y acentos (#15).
-- Un btree no sirve para un patron '%texto%': hace falta un indice de
-- trigramas. pg_trgm es una extension "trusted", asi que el rol dueno de
-- la base puede instalarla sin ser superusuario, que es justo lo que
-- permite que esta migracion corra con el usuario de la aplicacion.
CREATE EXTENSION IF NOT EXISTS pg_trgm;


CREATE TABLE professional_category (
    id          BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    enabled     BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_professional_category PRIMARY KEY (id)
);

COMMENT ON TABLE professional_category IS
    'Copia local de professionalCategories del snapshot (§7). El id es el de la catedra.';


CREATE TABLE professional (
    id          BIGINT       NOT NULL,
    category_id BIGINT       NOT NULL,
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255) NOT NULL,
    -- Nombre completo normalizado (minusculas, sin acentos) que alimenta la
    -- busqueda parcial. Lo escribe la aplicacion al aplicar el snapshot o un
    -- cambio incremental; no es un campo del contrato.
    --
    -- Se elige una columna normalizada en lugar de un indice funcional sobre
    -- lower(unaccent(...)) porque unaccent() es STABLE y no IMMUTABLE:
    -- PostgreSQL no la acepta en un indice sin envolverla en una funcion
    -- propia declarada IMMUTABLE, una declaracion que en realidad miente
    -- (el resultado cambia si cambia el diccionario) y que puede corromper
    -- el indice en silencio. Ademas, normalizar en el dominio deja la
    -- definicion de "nombre buscable" donde se puede probar.
    search_name VARCHAR(511) NOT NULL,
    enabled     BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_professional PRIMARY KEY (id),

    -- Sin ON DELETE CASCADE a proposito. Las entidades deshabilitadas se
    -- conservan como baja logica (§14.4), asi que en el flujo normal no hay
    -- borrado fisico. El unico borrado posible es el reemplazo de la copia
    -- al aplicar un snapshot completo (#13), y ahi se borra en orden
    -- (horarios, profesionales, categorias): es explicito y revisable.
    -- CASCADE convertiria un DELETE mal apuntado en una perdida silenciosa.
    CONSTRAINT fk_professional_category FOREIGN KEY (category_id)
        REFERENCES professional_category (id)
        ON DELETE RESTRICT
);

COMMENT ON TABLE professional IS
    'Copia local de professionals del snapshot (§7). El id es el de la catedra.';
COMMENT ON COLUMN professional.search_name IS
    'first_name + last_name normalizados por la aplicacion para la busqueda parcial (#15).';


CREATE TABLE weekly_schedule (
    id                    BIGINT      NOT NULL,
    professional_id       BIGINT      NOT NULL,
    day_of_week           VARCHAR(9)  NOT NULL,
    start_time            TIME        NOT NULL,
    end_time              TIME        NOT NULL,
    slot_duration_minutes INTEGER     NOT NULL,
    enabled               BOOLEAN     NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_weekly_schedule PRIMARY KEY (id),

    CONSTRAINT fk_weekly_schedule_professional FOREIGN KEY (professional_id)
        REFERENCES professional (id)
        ON DELETE RESTRICT,

    -- El contrato enumera los siete valores de forma cerrada (§7), asi que
    -- restringirlos no inventa nada. Se usa VARCHAR con CHECK y no un tipo
    -- ENUM de PostgreSQL porque agregar o quitar valores de un ENUM es una
    -- migracion incomoda, y un CHECK se reemplaza sin tocar el tipo.
    CONSTRAINT ck_weekly_schedule_day_of_week CHECK (day_of_week IN (
        'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
        'FRIDAY', 'SATURDAY', 'SUNDAY'
    )),
    CONSTRAINT ck_weekly_schedule_time_range CHECK (end_time > start_time),
    CONSTRAINT ck_weekly_schedule_slot_duration CHECK (slot_duration_minutes > 0)
);

COMMENT ON TABLE weekly_schedule IS
    'Copia local de weeklySchedules del snapshot (§7). El id es el de la catedra.';


-- --- Indices de los filtros obligatorios (§4.1) -------------------------
--
-- Los filtros minimos son categoria, nombre, habilitado y disponibilidad.
--
-- No hay un indice suelto sobre enabled: es un booleano de cardinalidad 2 y
-- por si solo el planificador prefiere un scan secuencial. Donde sirve es
-- como segunda columna del indice por categoria, que es la combinacion real
-- de las consultas ("profesionales habilitados de esta categoria").

CREATE INDEX ix_professional_category_enabled
    ON professional (category_id, enabled);

CREATE INDEX ix_professional_search_name
    ON professional USING gin (search_name gin_trgm_ops);

-- Disponibilidad (#16): se resuelve cruzando los horarios del profesional.
CREATE INDEX ix_weekly_schedule_professional_enabled
    ON weekly_schedule (professional_id, enabled);

-- Listado de categorias habilitadas.
CREATE INDEX ix_professional_category_enabled_name
    ON professional_category (enabled, name);
