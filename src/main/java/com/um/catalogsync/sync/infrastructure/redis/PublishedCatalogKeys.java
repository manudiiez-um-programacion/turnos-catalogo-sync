package com.um.catalogsync.sync.infrastructure.redis;

/**
 * Las claves de Redis del catalogo publicado, tal como las nombra §14.
 *
 * <p>Estan en una clase propia y no como constantes del adaptador porque las
 * necesitan tres lugares: el adaptador para leer, el mapper para nombrar la
 * clave en los mensajes de error, y las pruebas para cargar los ejemplos de
 * la referencia. Si cada uno escribiera el literal, un cambio de nombre en
 * el contrato dejaria pruebas que pasan leyendo una clave que ya no existe.
 *
 * <p><b>Todas empiezan con {@code catedra:sync:}</b>, que es el unico
 * namespace del que este servicio lee. No hay ninguna constante para
 * {@code catedra:holds:*} —es interno de la catedra y se manipula
 * exclusivamente por REST (§14.1)— ni para {@code alumnos:{groupId}:*}, que
 * es el namespace propio y por ahora no se usa (§14.6).
 */
public final class PublishedCatalogKeys {

    /** Prefijo del namespace de lectura. */
    public static final String NAMESPACE = "catedra:sync:";

    /** String decimal con la ultima version publicada (§14.2). */
    public static final String CURRENT_VERSION = NAMESPACE + "current-version";

    /** String decimal con el borde de la ventana retenida (§14.2). */
    public static final String OLDEST_AVAILABLE_VERSION = NAMESPACE + "oldest-available-version";

    /** Hash con las dos versiones y el {@code publishedAt} (§14.3). */
    public static final String METADATA = NAMESPACE + "metadata";

    /** Hash de categorias, field = id, value = JSON (§14.4). */
    public static final String PROFESSIONAL_CATEGORIES = NAMESPACE + "professional-categories";

    /** Hash de profesionales, field = id, value = JSON (§14.4). */
    public static final String PROFESSIONALS = NAMESPACE + "professionals";

    /** Hash de horarios semanales, field = id, value = JSON (§14.4). */
    public static final String WEEKLY_SCHEDULES = NAMESPACE + "weekly-schedules";

    private PublishedCatalogKeys() {
    }
}
