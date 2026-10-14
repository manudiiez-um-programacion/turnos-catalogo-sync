/**
 * Capa {@code infrastructure} del slice {@code shared}.
 *
 * <p>Configuracion de Spring Security, el {@code @RestControllerAdvice} que
 * produce los errores en {@code application/problem+json} con {@code code}
 * estable (§13 de la referencia de integracion), la configuracion de OpenAPI,
 * la implementacion del reloj y las metricas.
 *
 * <p>Los adaptadores: framework, base de datos, HTTP, mensajeria. Todo lo
 * reemplazable. Depende de {@code application} y {@code domain}, nunca al
 * reves, y nada de acá sale hacia adentro: se mapea en el borde.
 *
 * <p>Estructura: {@code persistence/} entidad JPA, repositorio Spring Data,
 * mapper y adaptador; {@code web/} controlador, DTOs y mapper. Los slices
 * que hablan con la catedra agregan {@code client/}, {@code cache/} o
 * {@code messaging/} espejando el mismo patron.
 */
package com.um.catalogsync.shared.infrastructure;
