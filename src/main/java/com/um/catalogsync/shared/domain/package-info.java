/**
 * Capa {@code domain} del slice {@code shared}.
 *
 * <p>Tipos y puertos transversales que el dominio de cualquier slice puede
 * usar sin acoplarse al framework, por ejemplo el puerto de reloj que hace
 * testeable todo lo que depende del tiempo.
 *
 * <p>Java puro: sin Spring, sin JPA, sin Jackson, sin ninguna anotacion de
 * framework. No depende de nada fuera de este paquete.
 *
 * <p>Estructura: {@code model/} el objeto de negocio, {@code ports/in/} un
 * caso de uso por interfaz, {@code ports/out/} lo que el dominio necesita
 * del exterior.
 */
package com.um.catalogsync.shared.domain;
