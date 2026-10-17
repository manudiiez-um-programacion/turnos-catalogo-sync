/**
 * Capa {@code application} del slice {@code professional}.
 *
 * <p>Casos de uso de busqueda y consulta de profesionales, y la fachada del
 * slice. Acá vive la combinacion de filtros, no en el controlador.
 *
 * <p>Depende solo de {@code domain}. Orquesta el dominio e implementa los
 * puertos de entrada.
 *
 * <p>Estructura: {@code usecases/} una impl {@code @Component} por puerto de
 * entrada, {@code service/} la fachada {@code @Service} que compone los
 * casos de uso y es lo unico que la capa web invoca, {@code exception/} las
 * excepciones del slice.
 */
package com.um.catalogsync.professional.application;
