/**
 * Capa {@code infrastructure} del slice {@code professional}.
 *
 * <p>Persistencia JPA de profesionales y la API REST de busqueda que consume la
 * app KMP, protegida con JWT de usuario final.
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
package com.um.catalogsync.professional.infrastructure;
