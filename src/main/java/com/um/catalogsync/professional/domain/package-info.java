/**
 * Capa {@code domain} del slice {@code professional}.
 *
 * <p>El profesional como objeto de negocio, los criterios de busqueda y los
 * puertos del slice: busqueda y consulta hacia adentro, {@code upsert} y
 * {@code replaceAll} hacia afuera.
 *
 * <p>Java puro: sin Spring, sin JPA, sin Jackson, sin ninguna anotacion de
 * framework. No depende de nada fuera de este paquete.
 *
 * <p>Estructura: {@code model/} el objeto de negocio, {@code ports/in/} un
 * caso de uso por interfaz, {@code ports/out/} lo que el dominio necesita
 * del exterior.
 */
package com.um.catalogsync.professional.domain;
