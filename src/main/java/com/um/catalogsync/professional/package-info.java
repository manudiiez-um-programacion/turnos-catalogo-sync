/**
 * Slice {@code professional} — Profesionales de la copia local, y la busqueda con filtros.

 * <p>Dueño del modelo de profesional y de la busqueda sobre datos locales por
 * categoria, nombre, habilitado y disponibilidad (§4.1 del enunciado). Las
 * busquedas nunca consultan al servicio central. De solo lectura hacia afuera.
 *
 * <p>Regla de dependencias: {@code infrastructure} → {@code application} →
 * {@code domain}. Ninguna {@code @Entity} JPA, DTO web ni tipo de
 * {@code infrastructure} puede aparecer en una firma de {@code domain} o
 * {@code application}.
 */
package com.um.catalogsync.professional;
