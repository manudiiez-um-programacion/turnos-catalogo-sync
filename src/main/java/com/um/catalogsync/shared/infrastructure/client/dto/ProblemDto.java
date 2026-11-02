package com.um.catalogsync.shared.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Un error {@code application/problem+json} de la catedra (§13), reducido a
 * lo unico que un cliente tiene permitido usar para decidir.
 *
 * <p><b>Por que un solo campo.</b> §13 es terminante: "los clientes deben
 * decidir por {@code status} y {@code code}, nunca por el texto de
 * {@code detail}". Este record declara solo {@code code}, y la ausencia de
 * los demas campos no es un olvido, es la regla hecha codigo: si
 * {@code detail} no esta en el tipo, no hay forma de que una decision
 * termine dependiendo de el, ni hoy ni cuando alguien agregue un
 * {@code if} apurado.
 *
 * <p><b>Y por que tampoco {@code status}.</b> El cuerpo repite el codigo de
 * estado, pero el autoritativo es el de la linea de respuesta HTTP, que el
 * cliente ya tiene y que ningun intermediario puede desincronizar. Leer el
 * del cuerpo seria tener dos fuentes para el mismo dato y tener que decidir
 * cual gana.
 *
 * <p>{@code code} puede venir {@code null}: §13 avisa que los rechazos
 * {@code 401} y {@code 403} que produce Spring Security directamente no lo
 * incluyen. Quien traduce tiene que tolerarlo.
 *
 * <p>{@code ignoreUnknown} porque de este JSON se ignora casi todo a
 * proposito —{@code type}, {@code title}, {@code detail}, {@code message},
 * {@code path}, {@code fieldErrors}— y porque el contrato puede sumar
 * campos (§4). Va anotado en el DTO y no en una configuracion global de
 * Jackson: los clientes de la catedra se arman con los conversores por
 * defecto, asi que la tolerancia tiene que estar escrita donde no dependa de
 * eso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProblemDto(String code) {
}
