/**
 * Capa {@code infrastructure} del slice {@code category}.
 *
 * <p>Persistencia JPA de categorias (entidad, repositorio Spring Data, mapper y
 * adaptador) y, si hace falta, su exposicion REST de solo lectura.
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
package com.um.catalogsync.category.infrastructure;
