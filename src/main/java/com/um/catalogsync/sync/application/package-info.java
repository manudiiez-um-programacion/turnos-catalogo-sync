/**
 * Capa {@code application} del slice {@code sync}.
 *
 * <p>La logica de sincronizacion: aplicar un snapshot completo, aplicar los
 * cambios de una version incremental, decidir cuando hay discontinuidad y
 * avanzar la version local solo al terminar. Es lo que se prueba con dobles
 * de los puertos, sin Redis, Kafka ni la API de la catedra.
 *
 * <p>Depende solo de {@code domain}. Orquesta el dominio e implementa los
 * puertos de entrada.
 *
 * <p>Estructura: {@code usecases/} una impl {@code @Component} por puerto de
 * entrada, {@code service/} la fachada {@code @Service} que compone los
 * casos de uso y es lo unico que la capa web invoca, {@code exception/} las
 * excepciones del slice.
 */
package com.um.catalogsync.sync.application;
