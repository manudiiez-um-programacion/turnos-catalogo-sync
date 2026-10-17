/**
 * Capa {@code infrastructure} del slice {@code sync}.
 *
 * <p>Los adaptadores externos del slice: cliente REST del snapshot, adaptador de
 * Redis para versiones y cambios, consumidor Kafka de {@code CatalogUpdated},
 * persistencia de la version y el estado, y la API REST de estado de
 * sincronizacion.
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
package com.um.catalogsync.sync.infrastructure;
