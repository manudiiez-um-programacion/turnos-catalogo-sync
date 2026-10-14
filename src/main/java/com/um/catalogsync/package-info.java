/**
 * Servicio de catalogo y sincronizacion.
 *
 * <p>Mantiene la copia local del catalogo cuya fuente autoritativa es el
 * servicio central de la catedra, y la expone a la app KMP (busqueda) y al
 * servicio de turnos (agenda vigente).
 *
 * <p>La arquitectura es hexagonal con slices verticales: cada subpaquete
 * directo de este es un slice de negocio autocontenido con sus tres capas
 * adentro. La decision, la regla de dependencias y los adaptadores externos
 * estan en {@code docs/adr/0001-arquitectura-hexagonal.md}.
 *
 * <p>Slices:
 * <ul>
 *   <li>{@code category} — categorias profesionales</li>
 *   <li>{@code professional} — profesionales, busqueda y filtros</li>
 *   <li>{@code schedule} — horarios semanales y agenda vigente</li>
 *   <li>{@code sync} — version local, snapshot, incremental y estado</li>
 *   <li>{@code integration} — cuenta tecnica ante la catedra</li>
 *   <li>{@code shared} — transversales sin dueño de negocio</li>
 * </ul>
 */
package com.um.catalogsync;
