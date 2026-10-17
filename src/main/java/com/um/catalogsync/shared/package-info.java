/**
 * Slice {@code shared} — Transversales sin dueño de negocio.

 * <p>Seguridad, manejo de errores en {@code application/problem+json},
 * configuracion de OpenAPI, reloj inyectable y metricas. No modela ninguna
 * entidad del catalogo: lo que vive acá es lo que necesitan todos los slices.

 * <p>Mantiene las tres capas por consistencia, aunque {@code application}
 * puede quedar practicamente vacia.
 *
 * <p>Regla de dependencias: {@code infrastructure} → {@code application} →
 * {@code domain}. Ninguna {@code @Entity} JPA, DTO web ni tipo de
 * {@code infrastructure} puede aparecer en una firma de {@code domain} o
 * {@code application}.
 */
package com.um.catalogsync.shared;
