/**
 * Slice {@code sync} — El proceso de sincronizacion con el servicio de la catedra.

 * <p>Dueño de la version de catalogo aplicada, de la sincronizacion completa
 * por snapshot REST, de la incremental por Kafka y Redis, de la deduplicacion
 * por {@code eventId}, de la deteccion de discontinuidad y del estado y los
 * errores de sincronizacion.

 * <p>Es el unico slice que escribe en {@code category}, {@code professional} y
 * {@code schedule}, y lo hace contra sus puertos de salida, nunca contra sus
 * repositorios JPA. La version local avanza solo cuando la unidad de trabajo
 * termino completa.
 *
 * <p>Regla de dependencias: {@code infrastructure} → {@code application} →
 * {@code domain}. Ninguna {@code @Entity} JPA, DTO web ni tipo de
 * {@code infrastructure} puede aparecer en una firma de {@code domain} o
 * {@code application}.
 */
package com.um.catalogsync.sync;
