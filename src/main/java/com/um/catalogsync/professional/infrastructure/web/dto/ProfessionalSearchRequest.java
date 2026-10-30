package com.um.catalogsync.professional.infrastructure.web.dto;

import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
import com.um.catalogsync.schedule.domain.model.AvailabilityCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Los parametros de busqueda tal como llegan por query string.
 *
 * <p><b>Es un DTO propio del borde web y no el criterio de dominio.</b> Lo que
 * los separa es que aca todo puede venir ausente o mal: {@code page} es un
 * {@code Integer} para distinguir "no lo mando" de "mando 0", y los limites se
 * declaran como validacion para que un valor fuera de rango sea un 400 con
 * {@code code} estable en lugar de una excepcion a mitad del caso de uso.
 *
 * <p><b>Los limites referencian las constantes del dominio</b> en lugar de
 * repetir los numeros. Si el tope de pagina cambiara, el mensaje de validacion
 * y la invariante del criterio no pueden quedar diciendo cosas distintas.
 *
 * <p><b>Los tres parametros de disponibilidad son cadenas</b> y no un
 * {@code DayOfWeek} con un {@code LocalTime}, aunque Spring sabria convertir
 * los dos. Es deliberado: si los convirtiera el binding, un dia inexistente o
 * una hora mal escrita darian un fallo de conversion genérico, con el
 * {@code code} del paginado y un mensaje que nombra clases de Java. Llegando
 * como texto, los parsea {@link AvailabilityCriteria} con las mismas funciones
 * que leyeron el snapshot, y el error sale con su propio {@code code} y un
 * mensaje que dice que corregir.
 *
 * @param categoryId categoria exacta; ausente para no filtrar
 * @param name termino para la coincidencia parcial sobre nombre y apellido;
 *     ausente o en blanco para no filtrar
 * @param enabled estado exacto; ausente para traer habilitados y
 *     deshabilitados
 * @param availableOn dia de la semana en el que tiene que tener agenda
 * @param availableFrom inicio de la franja horaria pedida
 * @param availableTo fin de la franja horaria pedida
 * @param page numero de pagina base cero; ausente es la primera
 * @param size elementos por pagina; ausente es {@value
 *     ProfessionalSearchCriteria#TAMANIO_POR_DEFECTO}
 */
@Schema(name = "ProfessionalSearchRequest", description = "Filtros y pagina de la busqueda")
public record ProfessionalSearchRequest(
        @Schema(description = "Id de la categoria profesional", example = "7") Long categoryId,
        @Schema(
                        description =
                                "Coincidencia parcial sobre nombre y apellido, insensible a"
                                        + " mayusculas y acentos: 'gomez' encuentra a 'Gómez'",
                        example = "ana gomez")
                @Size(max = 120, message = "el nombre no puede tener mas de 120 caracteres")
                String name,
        @Schema(description = "true solo habilitados, false solo deshabilitados", example = "true")
                Boolean enabled,
        @Schema(
                        description =
                                "Dia de la semana en el que el profesional tiene que tener"
                                        + " agenda. Responde 'tiene agenda', no 'tiene cupo"
                                        + " libre': no considera ocupaciones ni reservas, que"
                                        + " son del servicio de turnos.",
                        allowableValues = {
                            "MONDAY",
                            "TUESDAY",
                            "WEDNESDAY",
                            "THURSDAY",
                            "FRIDAY",
                            "SATURDAY",
                            "SUNDAY"
                        },
                        example = "MONDAY")
                String availableOn,
        @Schema(
                        description =
                                "Inicio de la franja pedida, HH:mm o HH:mm:ss. Se cruza por"
                                        + " solapamiento: un horario de 09:00 a 13:00 cumple un"
                                        + " pedido de 10:00 a 11:00. Va siempre junto con"
                                        + " availableTo.",
                        example = "09:00")
                String availableFrom,
        @Schema(
                        description = "Fin de la franja pedida, posterior a availableFrom",
                        example = "13:00")
                String availableTo,
        @Schema(description = "Pagina, base cero", defaultValue = "0", example = "0")
                @Min(value = 0, message = "la pagina no puede ser negativa")
                Integer page,
        @Schema(description = "Elementos por pagina", defaultValue = "20", example = "20")
                @Min(value = 1, message = "el tamaño de pagina minimo es 1")
                @Max(
                        value = ProfessionalSearchCriteria.TAMANIO_MAXIMO,
                        message = "el tamaño de pagina maximo es "
                                + ProfessionalSearchCriteria.TAMANIO_MAXIMO)
                Integer size) {

    /**
     * Al criterio de dominio, resolviendo los ausentes.
     *
     * <p>Los valores por defecto se aplican aca, en el borde, y no en el
     * dominio: "el cliente no mando pagina" es un hecho de HTTP, y el criterio
     * de dominio tiene que poder exigir una pagina concreta sin que nadie se
     * la invente despues. El nombre no se normaliza aca y el filtro de
     * disponibilidad no se valida aca, a proposito: las dos son decisiones del
     * dominio y las toman {@link ProfessionalSearchCriteria} y
     * {@link AvailabilityCriteria}, para que no haya dos lugares que puedan
     * decidir distinto.
     *
     * @throws com.um.catalogsync.schedule.domain.model.InvalidAvailabilityFilterException
     *     si los parametros de disponibilidad no se pueden interpretar
     */
    public ProfessionalSearchCriteria toCriteria() {
        return new ProfessionalSearchCriteria(
                categoryId,
                name,
                enabled,
                AvailabilityCriteria.de(availableOn, availableFrom, availableTo).orElse(null),
                page == null ? 0 : page,
                size == null ? ProfessionalSearchCriteria.TAMANIO_POR_DEFECTO : size);
    }
}
