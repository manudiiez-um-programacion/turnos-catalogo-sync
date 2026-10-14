/**
 * Capa {@code application} del slice {@code shared}.
 *
 * <p>Orquestacion transversal, si alguna hace falta. Puede quedar vacia: la
 * mayor parte de lo transversal es configuracion, y eso va en
 * {@code infrastructure}.
 *
 * <p>Depende solo de {@code domain}. Orquesta el dominio e implementa los
 * puertos de entrada.
 *
 * <p>Estructura: {@code usecases/} una impl {@code @Component} por puerto de
 * entrada, {@code service/} la fachada {@code @Service} que compone los
 * casos de uso y es lo unico que la capa web invoca, {@code exception/} las
 * excepciones del slice.
 */
package com.um.catalogsync.shared.application;
