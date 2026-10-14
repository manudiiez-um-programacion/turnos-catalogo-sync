/**
 * Capa {@code domain} del slice {@code integration}.
 *
 * <p>La credencial tecnica como objeto de negocio y el puerto que la provee a
 * quien la necesita, sin revelar como se obtiene.
 *
 * <p>Java puro: sin Spring, sin JPA, sin Jackson, sin ninguna anotacion de
 * framework. No depende de nada fuera de este paquete.
 *
 * <p>Estructura: {@code model/} el objeto de negocio, {@code ports/in/} un
 * caso de uso por interfaz, {@code ports/out/} lo que el dominio necesita
 * del exterior.
 */
package com.um.catalogsync.integration.domain;
