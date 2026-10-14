/**
 * Capa {@code application} del slice {@code category}.
 *
 * <p>Casos de uso de consulta de categorias y la fachada del slice, que es lo
 * unico que la capa web y los otros slices invocan.
 *
 * <p>Depende solo de {@code domain}. Orquesta el dominio e implementa los
 * puertos de entrada.
 *
 * <p>Estructura: {@code usecases/} una impl {@code @Component} por puerto de
 * entrada, {@code service/} la fachada {@code @Service} que compone los
 * casos de uso y es lo unico que la capa web invoca, {@code exception/} las
 * excepciones del slice.
 */
package com.um.catalogsync.category.application;
