/**
 * Capa {@code infrastructure} del slice {@code integration}.
 *
 * <p>Cliente REST del login tecnico de la catedra y la configuracion
 * externalizada de acceso. Ningun valor sensible va versionado: todo entra
 * por variable de entorno.
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
package com.um.catalogsync.integration.infrastructure;
