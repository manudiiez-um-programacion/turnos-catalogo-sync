/**
 * Capa {@code domain} del slice {@code sync}.
 *
 * <p>La version local, el estado de sincronizacion y los puertos de salida
 * hacia el mundo externo: snapshot REST, versiones y cambios en Redis, y
 * escritura en los slices de catalogo. Ningun tipo de Spring, JPA, Jackson,
 * Lettuce ni Kafka entra acá.
 *
 * <p>Java puro: sin Spring, sin JPA, sin Jackson, sin ninguna anotacion de
 * framework. No depende de nada fuera de este paquete.
 *
 * <p>Estructura: {@code model/} el objeto de negocio, {@code ports/in/} un
 * caso de uso por interfaz, {@code ports/out/} lo que el dominio necesita
 * del exterior.
 */
package com.um.catalogsync.sync.domain;
