/**
 * Capa {@code application} del slice {@code schedule}.
 *
 * <p>Casos de uso de consulta de horarios y de armado de la agenda vigente, y
 * la fachada del slice.
 *
 * <p>Depende solo de {@code domain}. Orquesta el dominio e implementa los
 * puertos de entrada.
 *
 * <p>Estructura: {@code usecases/} una impl {@code @Component} por puerto de
 * entrada, {@code service/} la fachada {@code @Service} que compone los
 * casos de uso y es lo unico que la capa web invoca, {@code exception/} las
 * excepciones del slice.
 */
package com.um.catalogsync.schedule.application;
