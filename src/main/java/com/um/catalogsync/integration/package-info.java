/**
 * Slice {@code integration} — Cuenta tecnica del proyecto ante el servicio de la catedra.

 * <p>Dueño de la configuracion de acceso y de la obtencion y renovacion del
 * JWT tecnico. Es el unico slice que conoce esas credenciales: los demas lo
 * consumen por un puerto. El JWT tecnico nunca se expone a la app KMP.
 *
 * <p>Regla de dependencias: {@code infrastructure} → {@code application} →
 * {@code domain}. Ninguna {@code @Entity} JPA, DTO web ni tipo de
 * {@code infrastructure} puede aparecer en una firma de {@code domain} o
 * {@code application}.
 */
package com.um.catalogsync.integration;
