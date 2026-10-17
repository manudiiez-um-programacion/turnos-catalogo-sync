/**
 * Slice {@code category} — Categorias profesionales de la copia local.

 * <p>Dueño del modelo, la persistencia y la consulta de categorias. De solo
 * lectura hacia afuera: no expone creacion, modificacion ni borrado por HTTP,
 * porque la unica escritura legitima viene del slice {@code sync}.
 *
 * <p>Regla de dependencias: {@code infrastructure} → {@code application} →
 * {@code domain}. Ninguna {@code @Entity} JPA, DTO web ni tipo de
 * {@code infrastructure} puede aparecer en una firma de {@code domain} o
 * {@code application}.
 */
package com.um.catalogsync.category;
