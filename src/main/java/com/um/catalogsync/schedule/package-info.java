/**
 * Slice {@code schedule} — Horarios semanales y agenda vigente.

 * <p>Dueño de los horarios semanales de la copia local y del armado de la
 * agenda vigente que consume el servicio de turnos por contrato REST con JWT.
 * No calcula disponibilidad de turnos ni reservas: eso es del otro servicio.
 *
 * <p>Regla de dependencias: {@code infrastructure} → {@code application} →
 * {@code domain}. Ninguna {@code @Entity} JPA, DTO web ni tipo de
 * {@code infrastructure} puede aparecer en una firma de {@code domain} o
 * {@code application}.
 */
package com.um.catalogsync.schedule;
