/**
 * Capa {@code domain} del slice {@code category}.
 *
 * <p>La categoria como objeto de negocio y los puertos que el slice necesita:
 * consulta por id y por nombre hacia adentro, {@code upsert} y
 * {@code replaceAll} hacia afuera para que {@code sync} aplique el catalogo.
 *
 * <p>Java puro: sin Spring, sin JPA, sin Jackson, sin ninguna anotacion de
 * framework. No depende de nada fuera de este paquete.
 *
 * <p>Estructura: {@code model/} el objeto de negocio, {@code ports/in/} un
 * caso de uso por interfaz, {@code ports/out/} lo que el dominio necesita
 * del exterior.
 */
package com.um.catalogsync.category.domain;
