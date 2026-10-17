/**
 * Capa {@code domain} del slice {@code schedule}.
 *
 * <p>El horario semanal como objeto de negocio, la agenda vigente, y los
 * puertos del slice: consulta de agenda hacia adentro, {@code upsert} y
 * {@code replaceAll} hacia afuera.
 *
 * <p>Java puro: sin Spring, sin JPA, sin Jackson, sin ninguna anotacion de
 * framework. No depende de nada fuera de este paquete.
 *
 * <p>Estructura: {@code model/} el objeto de negocio, {@code ports/in/} un
 * caso de uso por interfaz, {@code ports/out/} lo que el dominio necesita
 * del exterior.
 */
package com.um.catalogsync.schedule.domain;
