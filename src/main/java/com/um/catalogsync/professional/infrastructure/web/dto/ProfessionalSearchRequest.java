package com.um.catalogsync.professional.infrastructure.web.dto;

import com.um.catalogsync.professional.domain.model.ProfessionalSearchCriteria;
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
 * @param categoryId categoria exacta; ausente para no filtrar
 * @param name termino para la coincidencia parcial sobre nombre y apellido;
 *     ausente o en blanco para no filtrar
 * @param enabled estado exacto; ausente para traer habilitados y
 *     deshabilitados
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
     * la invente despues. El nombre no se normaliza aca a proposito: esa es
     * una decision del dominio y la toma el constructor de
     * {@link ProfessionalSearchCriteria}, para que no haya dos lugares que
     * puedan normalizar distinto.
     */
    public ProfessionalSearchCriteria toCriteria() {
        return new ProfessionalSearchCriteria(
                categoryId,
                name,
                enabled,
                page == null ? 0 : page,
                size == null ? ProfessionalSearchCriteria.TAMANIO_POR_DEFECTO : size);
    }
}
